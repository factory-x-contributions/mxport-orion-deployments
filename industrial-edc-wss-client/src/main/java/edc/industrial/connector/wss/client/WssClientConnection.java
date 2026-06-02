package edc.industrial.connector.wss.client;

import edc.industrial.connector.wss.client.config.ClientConfig;
import edc.industrial.connector.wss.client.transfer.ActiveTransferManager;
import edc.industrial.connector.wss.client.transfer.TransferCommand;
import edc.industrial.connector.wss.client.transfer.TransferCommandParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the WebSocket connection to the EDC Industrial WSS server.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Establish and maintain the WebSocket connection (with API-key authentication)</li>
 *   <li>Parse incoming JSON commands into {@link TransferCommand} objects</li>
 *   <li>Delegate commands to the {@link ActiveTransferManager}</li>
 *   <li>Reconnect automatically on disconnection</li>
 * </ul>
 *
 * <p>The connection URL is built as:
 * <pre>{@code <wssEndpoint>?clientId=<clientId>&apiKey=<apiKey>}</pre>
 * Additionally, the API key is sent as an HTTP header {@code X-Api-Key} during the
 * WebSocket handshake so servers that validate headers are also supported.
 */
public class WssClientConnection {

    private static final Logger log = LoggerFactory.getLogger(WssClientConnection.class);

    private final ClientConfig config;
    private final ActiveTransferManager transferManager;
    private final TransferCommandParser commandParser = new TransferCommandParser();
    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean intentionalClose = new AtomicBoolean(false);

    private InternalWsClient wsClient;
    private ScheduledFuture<?> pingTask;

    public WssClientConnection(ClientConfig config, ActiveTransferManager transferManager) {
        this.config = config;
        this.transferManager = transferManager;
    }

    // ---- Lifecycle -----------------------------------------------------------------

    /** Connects to the WSS server (blocking until the first connection attempt completes). */
    public void connect() throws Exception {
        running.set(true);
        intentionalClose.set(false);
        doConnect();
    }

    /** Gracefully shuts down the connection and all transfers. */
    public void close() {
        intentionalClose.set(true);
        running.set(false);

        if (pingTask != null) {
            pingTask.cancel(true);
        }

        if (wsClient != null) {
            wsClient.close();
        }

        reconnectScheduler.shutdownNow();
        transferManager.shutdown();
        log.info("WssClientConnection closed");
    }

    // ---- Private helpers -----------------------------------------------------------

    private void doConnect() throws Exception {
        URI uri = buildUri();
        Map<String, String> headers = buildHeaders();

        log.info("Connecting to WSS server: {}", uri);

        wsClient = new InternalWsClient(uri, headers);

        // connectBlocking() waits until either connected or connection fails (throws on timeout)
        boolean connected = wsClient.connectBlocking(15, TimeUnit.SECONDS);
        if (!connected) {
            log.warn("Initial WSS connection attempt timed out – will retry");
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running.get() || intentionalClose.get()) return;

        log.info("Scheduling reconnect in {}ms", config.getReconnectIntervalMs());
        reconnectScheduler.schedule(() -> {
            if (!running.get() || intentionalClose.get()) return;
            try {
                doConnect();
            } catch (Exception e) {
                log.warn("Reconnect attempt failed: {}", e.getMessage());
                scheduleReconnect();
            }
        }, config.getReconnectIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void startPingTask() {
        if (pingTask != null && !pingTask.isCancelled()) return;
        pingTask = reconnectScheduler.scheduleAtFixedRate(() -> {
            if (wsClient != null && wsClient.isOpen()) {
                String ping = String.format(
                        "{\"type\":\"ping\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                        config.getClientId(), Instant.now());
                wsClient.send(ping);
                log.trace("Sent ping");
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private URI buildUri() throws Exception {
        String base = config.getWssEndpoint();
        String sep = base.contains("?") ? "&" : "?";
        StringBuilder sb = new StringBuilder(base)
                .append(sep).append("clientId=").append(config.getClientId());
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            sb.append("&apiKey=").append(config.getApiKey());
        }
        return new URI(sb.toString());
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            headers.put("X-Api-Key", config.getApiKey());
            headers.put("Authorization", "ApiKey " + config.getApiKey());
        }
        return headers;
    }

    /** Message types the server sends that we intentionally do not act on. */
    private static final java.util.Set<String> SILENT_TYPES = java.util.Set.of(
            "pong", "welcome", "error", "ack", "subscribed", "unsubscribed", "response", "transfer_ack"
    );

    private void handleMessage(String message) {
        log.debug("Received message: {}", message);

        TransferCommand cmd = commandParser.parse(message);

        switch (cmd.getType()) {
            case OPCUA_READ_REQUEST -> {
                log.info("Handling OPCUA_READ_REQUEST for transfer {}", cmd.getTransferId());
                transferManager.startTransfer(cmd);
                sendAck(cmd.getTransferId(), "started");
            }
            case SUSPEND_TRANSFER -> {
                log.info("Handling SUSPEND_TRANSFER for transfer {}", cmd.getTransferId());
                transferManager.suspendTransfer(cmd.getTransferId());
                sendAck(cmd.getTransferId(), "suspended");
            }
            case TERMINATE_TRANSFER -> {
                log.info("Handling TERMINATE_TRANSFER for transfer {}", cmd.getTransferId());
                transferManager.terminateTransfer(cmd.getTransferId());
                sendAck(cmd.getTransferId(), "terminated");
            }
            case UNKNOWN -> {
                try {
                    String type = jsonMapper.readTree(message).path("type").asText("");
                    if (!SILENT_TYPES.contains(type.toLowerCase())) {
                        log.debug("Ignoring unknown message type: {}", type);
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    private void sendAck(String transferId, String status) {
        if (wsClient == null || !wsClient.isOpen()) return;
        String ack = String.format(
                "{\"type\":\"transfer_ack\",\"transferId\":\"%s\",\"status\":\"%s\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                transferId, status, config.getClientId(), Instant.now());
        wsClient.send(ack);
    }

    // ---- Inner WebSocket client implementation -------------------------------------

    private class InternalWsClient extends WebSocketClient {

        InternalWsClient(URI serverUri, Map<String, String> httpHeaders) {
            super(serverUri, httpHeaders);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.info("WebSocket connection opened (HTTP status: {})", handshake.getHttpStatus());
            startPingTask();
            // Send an initial ping so the server registers our session immediately.
            // (The server handles "ping" and replies with "pong".)
            String ping = String.format(
                    "{\"type\":\"ping\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                    config.getClientId(), Instant.now());
            send(ping);
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.info("WebSocket closed (code={}, reason='{}', remote={})", code, reason, remote);
            if (pingTask != null) {
                pingTask.cancel(true);
                pingTask = null;
            }
            if (remote && running.get() && !intentionalClose.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            log.error("WebSocket error: {}", ex.getMessage(), ex);
        }
    }
}

