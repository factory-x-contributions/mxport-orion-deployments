package edc.industrial.connector.wss.client.transfer;

import edc.industrial.connector.wss.client.config.ClientConfig;
import edc.industrial.connector.wss.client.mqtt.MqttPublisherService;
import edc.industrial.connector.wss.client.mqtt.PahoMqttPublisherServiceImpl;
import edc.industrial.connector.wss.client.opcua.OpcUaClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of active OPC-UA → MQTT push tasks on the client side.
 *
 * <p>One push task runs per unique MQTT topic. Multiple transfer IDs may map to the same topic,
 * avoiding duplicate data publication.
 *
 * <h2>TLS / certificate authentication</h2>
 * When the EDC server sends an {@code opcua_read_request} with {@code authType=certificate}:
 * <ul>
 *   <li>{@code caChain} in the command – inline PEM CA chain used to verify the MQTT broker TLS certificate.</li>
 *   <li>{@code MQTT_CLIENT_CERT_PATH} / {@code MQTT_CLIENT_KEY_PATH} from {@link ClientConfig} –
 *       the client's own identity (certificate + private key). The private key is never transmitted
 *       over the wire; it must be pre-installed on this machine.</li>
 * </ul>
 * If the local client cert/key paths are not configured, the connection falls back to
 * server-side-only TLS (the broker certificate is still verified, but no client certificate is sent).
 */
public class ActiveTransferManager {

    private static final Logger log = LoggerFactory.getLogger(ActiveTransferManager.class);

    private final OpcUaClientService opcUaClientService;
    private final ClientConfig config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    /** transferId → mqttTopic */
    private final ConcurrentHashMap<String, String> transferToTopic = new ConcurrentHashMap<>();

    /** mqttTopic → running push task */
    private final ConcurrentHashMap<String, TopicPushTask> topicTasks = new ConcurrentHashMap<>();

    public ActiveTransferManager(OpcUaClientService opcUaClientService, ClientConfig config) {
        this.opcUaClientService = opcUaClientService;
        this.config = config;
    }

    // ---- Public API ----------------------------------------------------------------

    /**
     * Starts a push task for the given command. If a task already exists for the same
     * MQTT topic, the new transfer ID is simply registered against it.
     */
    public void startTransfer(TransferCommand cmd) {
        String transferId = cmd.getTransferId();
        String topic = cmd.getMqttTopic();

        if (transferId == null || transferId.isBlank()) {
            log.error("Cannot start transfer – transferId is blank");
            return;
        }
        if (topic == null || topic.isBlank()) {
            log.error("Cannot start transfer {} – mqttTopic is blank", transferId);
            return;
        }

        transferToTopic.put(transferId, topic);

        if (topicTasks.containsKey(topic)) {
            topicTasks.get(topic).addTransferId(transferId);
            log.info("Transfer {} added to existing push task for topic '{}'", transferId, topic);
            return;
        }

        // Create MQTT publisher for this topic/broker
        MqttPublisherService publisher;
        try {
            publisher = createPublisher(cmd);
        } catch (Exception e) {
            log.error("Failed to connect to MQTT broker for transfer {}: {}", transferId, e.getMessage(), e);
            return;
        }

        List<String> nodeIds = cmd.getNodeIds();
        if (nodeIds.isEmpty()) {
            log.error("No nodeIds specified for transfer {}", transferId);
            return;
        }

        String serverUrl = cmd.getOpcuaServer();
        // OPC-UA server URL resolution order (first non-blank wins):
        //   1. OPCUA_SERVER_URL from local config – overrides the Docker-internal hostname
        //      the server may send (e.g. "opc.tcp://opcua-server:4840").
        //   2. opcuaServer field from the server command – used when no local override is set.
        String configServerUrl = config.getOpcUaServerUrl();
        if (configServerUrl != null && !configServerUrl.isBlank()) {
            if (serverUrl != null && !serverUrl.isBlank() && !serverUrl.equals(configServerUrl)) {
                log.info("OPC-UA server URL overridden by local config: '{}' (server sent: '{}')",
                        configServerUrl, serverUrl);
            }
            serverUrl = configServerUrl;
        }
        if (serverUrl == null || serverUrl.isBlank()) {
            log.error("No OPC-UA server URL available for transfer {} – " +
                      "set OPCUA_SERVER_URL in your env file or ensure the server command contains opcuaServer",
                      transferId);
            return;
        }
        final String resolvedServerUrl = serverUrl;   // effectively-final copy for lambda capture
        long intervalMs = cmd.getPushIntervalMs() > 0 ? cmd.getPushIntervalMs() : 5000;

        log.info("Starting OPC-UA→MQTT push: topic='{}', server='{}', nodes={}, interval={}ms",
                topic, resolvedServerUrl, nodeIds, intervalMs);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                String payload = readAndFormat(resolvedServerUrl, nodeIds, topic);
                publisher.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0);
                log.debug("Published to topic '{}'", topic);
            } catch (Exception e) {
                log.error("Push failed for topic '{}': {}", topic, e.getMessage(), e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        TopicPushTask task = new TopicPushTask(topic, future, publisher);
        task.addTransferId(transferId);
        topicTasks.put(topic, task);

        log.info("Push task started for topic '{}' (transfer {})", topic, transferId);
    }

    /** Suspends (stops pushing for) a transfer. */
    public void suspendTransfer(String transferId) {
        log.info("Suspending transfer {}", transferId);
        stopTransfer(transferId);
    }

    /** Terminates a transfer and cleans up resources. */
    public void terminateTransfer(String transferId) {
        log.info("Terminating transfer {}", transferId);
        stopTransfer(transferId);
    }

    /** Returns true if a push task is currently active for the given transferId. */
    public boolean isActive(String transferId) {
        String topic = transferToTopic.get(transferId);
        if (topic == null) return false;
        TopicPushTask task = topicTasks.get(topic);
        return task != null && task.isRunning();
    }

    /** Shuts down all active tasks and the scheduler. */
    public void shutdown() {
        log.info("Shutting down ActiveTransferManager – stopping {} topic tasks", topicTasks.size());
        topicTasks.values().forEach(TopicPushTask::cancel);
        topicTasks.clear();
        transferToTopic.clear();
        scheduler.shutdownNow();
    }

    // ---- Private helpers -----------------------------------------------------------

    private void stopTransfer(String transferId) {
        String topic = transferToTopic.remove(transferId);
        if (topic == null) {
            log.debug("No active topic mapping for transfer {}", transferId);
            return;
        }

        TopicPushTask task = topicTasks.get(topic);
        if (task == null) return;

        task.removeTransferId(transferId);

        if (!task.hasTransfers()) {
            task.cancel();
            topicTasks.remove(topic);
            log.info("Push task for topic '{}' stopped (no remaining transfers)", topic);
        } else {
            log.info("Transfer {} removed from topic '{}' ({} remaining)",
                    transferId, topic, task.getTransferCount());
        }
    }

    /**
     * Creates the MQTT publisher for a transfer command.
     *
     * <h3>Certificate auth strategy</h3>
     * The CA chain may arrive as inline PEM in the command (from the server).
     * The client certificate and private key are always read from local config
     * ({@code MQTT_CLIENT_CERT_PATH} / {@code MQTT_CLIENT_KEY_PATH}) – they are
     * never transmitted over the wire.
     *
     * <p>Fallback chain:
     * <ol>
     *   <li>Certificate auth (authType=certificate) – uses inline CA chain from command
     *       + local client cert/key from config. If no local cert/key is configured,
     *       server-side-only TLS is used (broker cert verified, no mTLS).</li>
     *   <li>Password auth (authType=password, or username+password present in command).</li>
     *   <li>Config MQTT credentials (MQTT_USERNAME / MQTT_PASSWORD from env file).</li>
     *   <li>Anonymous (no credentials at all).</li>
     * </ol>
     */
    private MqttPublisherService createPublisher(TransferCommand cmd) throws Exception {
        // Broker URL resolution order (first non-blank wins):
        //   1. MQTT_BROKER_URL from local config  – lets operators override the server-provided
        //      URL, which may contain a Docker-internal hostname unreachable from this machine.
        //   2. mqttBroker field from the server command  – used when no local override is set.
        String configBrokerUrl = config.getMqttBrokerUrl();
        String cmdBrokerUrl    = cmd.getMqttBroker();
        String brokerUrl;
        if (configBrokerUrl != null && !configBrokerUrl.isBlank()) {
            brokerUrl = configBrokerUrl;
            if (cmdBrokerUrl != null && !cmdBrokerUrl.isBlank() && !cmdBrokerUrl.equals(configBrokerUrl)) {
                log.info("MQTT broker URL overridden by local config: '{}' (server sent: '{}')",
                        brokerUrl, cmdBrokerUrl);
            }
        } else if (cmdBrokerUrl != null && !cmdBrokerUrl.isBlank()) {
            brokerUrl = cmdBrokerUrl;
            log.debug("MQTT broker URL taken from server command: '{}'", brokerUrl);
        } else {
            throw new IllegalStateException(
                    "No MQTT broker URL available: neither MQTT_BROKER_URL is configured " +
                    "nor did the server command contain a mqttBroker field.");
        }

        if (cmd.isCertificateAuth()) {
            // CA chain: comes inline from the server command (verifies broker cert)
            String caChain = cmd.getCaChain();

            // Client identity: always comes from local configuration (never transmitted)
            String clientCertPath = config.getMqttClientCertPath();
            String clientKeyPath  = config.getMqttClientKeyPath();

            // Fall back to config CA cert path if no inline CA chain was provided
            if (caChain == null || caChain.isBlank()) {
                caChain = config.getMqttCaCertPath();
                log.debug("No inline CA chain in command – using config path: {}", caChain);
            }

            if (caChain == null || caChain.isBlank()) {
                throw new IllegalStateException(
                        "Certificate auth requested but no CA chain available " +
                        "(neither inline in command nor MQTT_CA_CERT_PATH configured)");
            }

            // If caChain is a file path (not inline PEM), validate it exists
            if (!caChain.contains("-----BEGIN ") && !caChain.contains("\\n-----BEGIN ")) {
                java.nio.file.Path absCa = Paths.get(caChain).toAbsolutePath();
                if (!Files.exists(absCa)) {
                    throw new java.io.FileNotFoundException(
                            "CA certificate file not found: " + absCa +
                            "\n  Configured path : " + caChain +
                            "\n  Working directory: " + Paths.get("").toAbsolutePath() +
                            "\n  → Set MQTT_CA_CERT_PATH to the correct absolute path, " +
                            "or provide an inline PEM in the transfer command.");
                }
            }

            boolean hasMtls = clientCertPath != null && !clientCertPath.isBlank()
                           && clientKeyPath  != null && !clientKeyPath.isBlank();

            if (hasMtls) {
                validateCertFilesExist(clientCertPath, clientKeyPath);
                log.info("Creating TLS MQTT publisher (mTLS): broker={}, clientCert={}", brokerUrl, clientCertPath);
            } else {
                log.info("Creating TLS MQTT publisher (server-side TLS only, no client cert): broker={}", brokerUrl);
            }

            return new PahoMqttPublisherServiceImpl(brokerUrl, caChain, clientCertPath, clientKeyPath);
        }

        // Username/password: prefer command credentials, fall back to config
        String username = cmd.getUsername();
        String password = cmd.getPassword();

        if (username == null || username.isBlank()) {
            username = config.getMqttUsername();
            password = config.getMqttPassword();
        }

        log.info("Creating plain/password MQTT publisher: broker={}, user={}", brokerUrl,
                username != null ? "***" : "anonymous");
        return new PahoMqttPublisherServiceImpl(brokerUrl, username, password);
    }

    // ---- Cert validation -----------------------------------------------------------

    /**
     * Validates that the given cert/key file paths exist on the filesystem.
     * Resolves relative paths against the JVM working directory and includes
     * the absolute path in the error message so operators know exactly where
     * to place the files.
     */
    private static void validateCertFilesExist(String certPath, String keyPath) throws Exception {
        java.nio.file.Path absCert = Paths.get(certPath).toAbsolutePath();
        java.nio.file.Path absKey  = Paths.get(keyPath).toAbsolutePath();

        if (!Files.exists(absCert)) {
            throw new java.io.FileNotFoundException(
                    "Client certificate file not found: " + absCert +
                    "\n  Configured path : " + certPath +
                    "\n  Working directory: " + Paths.get("").toAbsolutePath() +
                    "\n  → Set MQTT_CLIENT_CERT_PATH to the correct absolute path, " +
                    "or copy the certificate to the expected location.");
        }
        if (!Files.exists(absKey)) {
            throw new java.io.FileNotFoundException(
                    "Client private key file not found: " + absKey +
                    "\n  Configured path : " + keyPath +
                    "\n  Working directory: " + Paths.get("").toAbsolutePath() +
                    "\n  → Set MQTT_CLIENT_KEY_PATH to the correct absolute path, " +
                    "or copy the private key to the expected location.");
        }
    }

    // ---- OPC-UA reading ------------------------------------------------------------

    private String readAndFormat(String serverUrl, List<String> nodeIds, String topic) throws Exception {
        Instant ts = Instant.now();
        if (nodeIds.size() == 1) {
            Object value = opcUaClientService.readValue(serverUrl, nodeIds.get(0));
            return formatSingle(nodeIds.get(0), value, ts, topic);
        }
        return formatMulti(serverUrl, nodeIds, ts, topic);
    }

    private String formatSingle(String nodeId, Object value, Instant ts, String topic) {
        return "{" +
                "\"nodeId\":\"" + escapeJson(nodeId) + "\"," +
                "\"value\":" + formatValue(value) + "," +
                "\"timestamp\":\"" + ts + "\"," +
                "\"assetId\":\"" + escapeJson(topic) + "\"" +
                "}";
    }

    private String formatMulti(String serverUrl, List<String> nodeIds, Instant ts, String topic) {
        StringBuilder sb = new StringBuilder("{\"timestamp\":\"")
                .append(ts).append("\",\"assetId\":\"").append(escapeJson(topic)).append("\",\"values\":[");

        for (int i = 0; i < nodeIds.size(); i++) {
            if (i > 0) sb.append(",");
            try {
                Object value = opcUaClientService.readValue(serverUrl, nodeIds.get(i));
                sb.append("{\"nodeId\":\"").append(escapeJson(nodeIds.get(i)))
                        .append("\",\"value\":").append(formatValue(value)).append("}");
            } catch (Exception e) {
                log.warn("Failed to read node '{}': {}", nodeIds.get(i), e.getMessage());
                sb.append("{\"nodeId\":\"").append(escapeJson(nodeIds.get(i)))
                        .append("\",\"error\":\"").append(escapeJson(e.getMessage())).append("\"}");
            }
        }

        sb.append("]}");
        return sb.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        String s = value.toString();
        try {
            Double.parseDouble(s);
            return s;
        } catch (NumberFormatException e) {
            return "\"" + escapeJson(s) + "\"";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ---- Inner task holder ---------------------------------------------------------

    private static class TopicPushTask {

        private final String topic;
        private final ScheduledFuture<?> future;
        private final MqttPublisherService publisher;
        private final java.util.Set<String> transferIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

        TopicPushTask(String topic, ScheduledFuture<?> future, MqttPublisherService publisher) {
            this.topic = topic;
            this.future = future;
            this.publisher = publisher;
        }

        void addTransferId(String id) { transferIds.add(id); }
        void removeTransferId(String id) { transferIds.remove(id); }
        boolean hasTransfers() { return !transferIds.isEmpty(); }
        int getTransferCount() { return transferIds.size(); }
        boolean isRunning() { return !future.isCancelled() && !future.isDone(); }

        void cancel() {
            future.cancel(true);
            publisher.close();
        }
    }
}

