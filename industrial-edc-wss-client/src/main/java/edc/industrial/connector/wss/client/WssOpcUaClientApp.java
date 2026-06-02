package edc.industrial.connector.wss.client;

import edc.industrial.connector.wss.client.config.ClientConfig;
import edc.industrial.connector.wss.client.opcua.OpcUaClientService;
import edc.industrial.connector.wss.client.opcua.OpcUaClientServiceImpl;
import edc.industrial.connector.wss.client.transfer.ActiveTransferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the standalone OPC-UA WSS Client application.
 *
 * <h2>Purpose</h2>
 * <p>This application connects to an EDC Industrial WebSocket (WSS) server endpoint,
 * receives EDC DataFlow commands, and executes them by reading values from an OPC-UA
 * server and publishing them periodically to an MQTT broker – effectively acting as the
 * "field-side" counterpart of the {@code IndustrialConnectorWssTransferFlowImpl} running
 * inside the EDC connector.
 *
 * <h2>Supported commands (from WSS server)</h2>
 * <ul>
 *   <li>{@code opcua_read_request} – start periodically reading OPC-UA nodes → MQTT push</li>
 *   <li>{@code suspend_transfer}   – pause an active push task</li>
 *   <li>{@code terminate_transfer} – stop and clean up a push task</li>
 * </ul>
 *
 * <h2>Configuration (environment variables)</h2>
 * <pre>
 * WSS_ENDPOINT              - WebSocket server URL  (default: ws://localhost:8181/industrial-ws)
 * WSS_API_KEY               - API key for authentication
 * WSS_CLIENT_ID             - Client identifier (auto-generated if not set)
 * WSS_RECONNECT_INTERVAL_MS - Reconnect delay in ms (default: 5000)
 *
 * MQTT_BROKER_URL           - Fallback MQTT broker (default: tcp://localhost:1883)
 * MQTT_USERNAME             - MQTT username (optional)
 * MQTT_PASSWORD             - MQTT password (optional)
 * MQTT_CA_CERT_PATH         - Path to CA certificate for TLS (optional)
 * MQTT_CLIENT_CERT_PATH     - Path to client certificate for TLS (optional)
 * MQTT_CLIENT_KEY_PATH      - Path to client private key for TLS (optional)
 *
 * OPCUA_USERNAME            - OPC-UA username (optional, defaults to anonymous)
 * OPCUA_PASSWORD            - OPC-UA password (optional)
 * OPCUA_CLIENT_CERT_PATH    - Path to client certificate for OPC-UA TLS (optional)
 * OPCUA_CLIENT_KEY_PATH     - Path to client private key for OPC-UA TLS (optional)
 * </pre>
 */
public class WssOpcUaClientApp {

    private static final Logger log = LoggerFactory.getLogger(WssOpcUaClientApp.class);

    public static void main(String[] args) throws Exception {
        log.info("====================================================");
        log.info("  OPC-UA WSS Client  –  starting up");
        log.info("====================================================");

        // 1. Load configuration
        ClientConfig config = ClientConfig.fromEnvironment();
        log.info("Configuration loaded: {}", config);

        // 2. Build OPC-UA client based on auth settings
        OpcUaClientService opcUaClient = buildOpcUaClient(config);

        // 3. Build transfer manager
        ActiveTransferManager transferManager = new ActiveTransferManager(opcUaClient, config);

        // 4. Build WSS connection
        WssClientConnection connection = new WssClientConnection(config, transferManager);

        // 5. Register shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered – closing connection");
            connection.close();
        }, "shutdown-hook"));

        // 6. Connect (the application keeps running until JVM shutdown)
        connection.connect();

        log.info("Client running – waiting for commands from {}", config.getWssEndpoint());

        // Keep the main thread alive
        Thread.currentThread().join();
    }

    // ---- Private helpers -----------------------------------------------------------

    private static OpcUaClientService buildOpcUaClient(ClientConfig config) {
        if (config.hasOpcUaCertAuth()) {
            log.info("Using certificate-based OPC-UA authentication");
            return OpcUaClientServiceImpl.withCertificateAuth(
                    config.getOpcUaClientCertPath(),
                    config.getOpcUaClientKeyPath());
        }

        if (config.getOpcUaUsername() != null && !config.getOpcUaUsername().isBlank()) {
            log.info("Using username/password OPC-UA authentication (user: ***)")  ;
            return OpcUaClientServiceImpl.withCredentialsAuth(
                    config.getOpcUaUsername(),
                    config.getOpcUaPassword());
        }

        log.info("Using anonymous OPC-UA connection");
        return new OpcUaClientServiceImpl();
    }
}

