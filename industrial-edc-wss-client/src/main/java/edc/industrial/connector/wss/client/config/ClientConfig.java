package edc.industrial.connector.wss.client.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the OPC-UA WSS client application.
 * Values are loaded from environment variables or system properties.
 */
public class ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ClientConfig.class);

    /** Name of the env-file setting that overrides the default path. */
    private static final String ENV_FILE_PROPERTY = "WSS_CLIENT_ENV_FILE";
    /**
     * Default env file used when {@code WSS_CLIENT_ENV_FILE} is NOT set in the environment.
     *
     * <ul>
     *   <li><b>Local / IntelliJ</b> – no env var needed; {@code client.local.env} is loaded
     *       automatically from the project working directory.</li>
     *   <li><b>start.sh</b> – exports {@code WSS_CLIENT_ENV_FILE=client.local.env} explicitly
     *       (matches this default, kept for clarity).</li>
     *   <li><b>Docker / docker-compose</b> – the Dockerfile sets
     *       {@code ENV WSS_CLIENT_ENV_FILE=/app/client.env} which overrides this default;
     *       {@code client.docker.env} is mounted at {@code /app/client.env} by docker-compose.</li>
     * </ul>
     */
    private static final String DEFAULT_ENV_FILE   = "client.local.env";

    // Lazily initialised dotenv instance (shared for the whole JVM run)
    private static volatile Dotenv dotenv;

    private static Dotenv dotenv() {
        if (dotenv == null) {
            synchronized (ClientConfig.class) {
                if (dotenv == null) {
                    dotenv = buildDotenv();
                }
            }
        }
        return dotenv;
    }

    private static Dotenv buildDotenv() {
        // Allow the env-file path to be supplied as a real env var or system property
        String envFilePath = realEnv(ENV_FILE_PROPERTY, DEFAULT_ENV_FILE);

        // Split into directory + filename so dotenv-java can find it
        java.io.File f = new java.io.File(envFilePath);
        String dir      = f.getParent()  != null ? f.getParent()  : ".";
        String filename = f.getName();

        DotenvBuilder builder = Dotenv.configure()
                .directory(dir)
                .filename(filename)
                .ignoreIfMissing();   // don't crash if the file doesn't exist

        Dotenv loaded = builder.load();
        log.info("Env-file lookup: path='{}' (directory='{}', file='{}')", envFilePath, dir, filename);
        return loaded;
    }

    /** Resolve: real env var → dotenv entry → system property → defaultValue */
    private static String resolveEnv(String key, String defaultValue) {
        // 1. Real OS environment variable wins
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) return v;
        // 2. Dotenv entry
        v = dotenv().get(key, null);
        if (v != null && !v.isBlank()) return v;
        // 3. JVM system property (dotted lowercase form)
        v = System.getProperty(key.toLowerCase().replace('_', '.'), null);
        if (v != null && !v.isBlank()) return v;
        return defaultValue;
    }

    /** Read only real OS env / system property, used before dotenv is initialised. */
    private static String realEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) return v;
        return System.getProperty(key.toLowerCase().replace('_', '.'), defaultValue);
    }

    // ---- WSS Endpoint ----
    private final String wssEndpoint;
    private final String apiKey;
    private final String clientId;
    private final int reconnectIntervalMs;

    // ---- MQTT Broker (provider-managed, for push operations) ----
    private final String mqttBrokerUrl;
    private final String mqttUsername;
    private final String mqttPassword;
    private final String mqttCaCertPath;
    private final String mqttClientCertPath;
    private final String mqttClientKeyPath;

    // ---- OPC UA Client ----
    private final String opcUaServerUrl;   // local override for the server-provided OPC-UA URL
    private final String opcUaUsername;
    private final String opcUaPassword;
    private final String opcUaClientCertPath;
    private final String opcUaClientKeyPath;

    private ClientConfig(Builder builder) {
        this.wssEndpoint = builder.wssEndpoint;
        this.apiKey = builder.apiKey;
        this.clientId = builder.clientId;
        this.reconnectIntervalMs = builder.reconnectIntervalMs;
        this.mqttBrokerUrl = builder.mqttBrokerUrl;
        this.mqttUsername = builder.mqttUsername;
        this.mqttPassword = builder.mqttPassword;
        this.mqttCaCertPath = builder.mqttCaCertPath;
        this.mqttClientCertPath = builder.mqttClientCertPath;
        this.mqttClientKeyPath = builder.mqttClientKeyPath;
        this.opcUaServerUrl = builder.opcUaServerUrl;
        this.opcUaUsername = builder.opcUaUsername;
        this.opcUaPassword = builder.opcUaPassword;
        this.opcUaClientCertPath = builder.opcUaClientCertPath;
        this.opcUaClientKeyPath = builder.opcUaClientKeyPath;
    }

    /**
     * Loads configuration from environment variables / system properties.
     */
    public static ClientConfig fromEnvironment() {
        // Trigger dotenv loading so it is ready before any resolveEnv call
        dotenv();
        return new Builder()
                .wssEndpoint(resolveEnv("WSS_ENDPOINT", "ws://localhost:8181/industrial-ws"))
                .apiKey(resolveEnv("WSS_API_KEY", null))
                .clientId(resolveEnv("WSS_CLIENT_ID", "industrial-connector-wss-client-" + System.currentTimeMillis()))
                .reconnectIntervalMs(Integer.parseInt(resolveEnv("WSS_RECONNECT_INTERVAL_MS", "5000")))
                .mqttBrokerUrl(resolveEnv("MQTT_BROKER_URL", "tcp://localhost:1883"))
                .mqttUsername(resolveEnv("MQTT_USERNAME", null))
                .mqttPassword(resolveEnv("MQTT_PASSWORD", null))
                .mqttCaCertPath(resolveEnv("MQTT_CA_CERT_PATH", null))
                .mqttClientCertPath(resolveEnv("MQTT_CLIENT_CERT_PATH", null))
                .mqttClientKeyPath(resolveEnv("MQTT_CLIENT_KEY_PATH", null))
                .opcUaServerUrl(resolveEnv("OPCUA_SERVER_URL", null))
                .opcUaUsername(resolveEnv("OPCUA_USERNAME", null))
                .opcUaPassword(resolveEnv("OPCUA_PASSWORD", null))
                .opcUaClientCertPath(resolveEnv("OPCUA_CLIENT_CERT_PATH", null))
                .opcUaClientKeyPath(resolveEnv("OPCUA_CLIENT_KEY_PATH", null))
                .build();
    }


    public String getWssEndpoint() { return wssEndpoint; }
    public String getApiKey() { return apiKey; }
    public String getClientId() { return clientId; }
    public int getReconnectIntervalMs() { return reconnectIntervalMs; }
    public String getMqttBrokerUrl() { return mqttBrokerUrl; }
    public String getMqttUsername() { return mqttUsername; }
    public String getMqttPassword() { return mqttPassword; }
    public String getMqttCaCertPath() { return mqttCaCertPath; }
    public String getMqttClientCertPath() { return mqttClientCertPath; }
    public String getMqttClientKeyPath() { return mqttClientKeyPath; }
    public String getOpcUaServerUrl() { return opcUaServerUrl; }
    public String getOpcUaUsername() { return opcUaUsername; }
    public String getOpcUaPassword() { return opcUaPassword; }
    public String getOpcUaClientCertPath() { return opcUaClientCertPath; }
    public String getOpcUaClientKeyPath() { return opcUaClientKeyPath; }

    public boolean hasMqttCertAuth() {
        return mqttCaCertPath != null && mqttClientCertPath != null && mqttClientKeyPath != null;
    }

    public boolean hasOpcUaCertAuth() {
        return opcUaClientCertPath != null && opcUaClientKeyPath != null;
    }

    @Override
    public String toString() {
        return "ClientConfig{" +
                "wssEndpoint='" + wssEndpoint + '\'' +
                ", apiKey='" + (apiKey != null ? "***" : "null") + '\'' +
                ", clientId='" + clientId + '\'' +
                ", mqttBrokerUrl='" + mqttBrokerUrl + '\'' +
                ", mqttUsername='" + (mqttUsername != null ? "***" : "null") + '\'' +
                '}';
    }

    public static class Builder {
        private String wssEndpoint;
        private String apiKey;
        private String clientId;
        private int reconnectIntervalMs = 5000;
        private String mqttBrokerUrl;
        private String mqttUsername;
        private String mqttPassword;
        private String mqttCaCertPath;
        private String mqttClientCertPath;
        private String mqttClientKeyPath;
        private String opcUaServerUrl;
        private String opcUaUsername;
        private String opcUaPassword;
        private String opcUaClientCertPath;
        private String opcUaClientKeyPath;

        public Builder wssEndpoint(String v) { this.wssEndpoint = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder reconnectIntervalMs(int v) { this.reconnectIntervalMs = v; return this; }
        public Builder mqttBrokerUrl(String v) { this.mqttBrokerUrl = v; return this; }
        public Builder mqttUsername(String v) { this.mqttUsername = v; return this; }
        public Builder mqttPassword(String v) { this.mqttPassword = v; return this; }
        public Builder mqttCaCertPath(String v) { this.mqttCaCertPath = v; return this; }
        public Builder mqttClientCertPath(String v) { this.mqttClientCertPath = v; return this; }
        public Builder mqttClientKeyPath(String v) { this.mqttClientKeyPath = v; return this; }
        public Builder opcUaServerUrl(String v) { this.opcUaServerUrl = v; return this; }
        public Builder opcUaUsername(String v) { this.opcUaUsername = v; return this; }
        public Builder opcUaPassword(String v) { this.opcUaPassword = v; return this; }
        public Builder opcUaClientCertPath(String v) { this.opcUaClientCertPath = v; return this; }
        public Builder opcUaClientKeyPath(String v) { this.opcUaClientKeyPath = v; return this; }
        public ClientConfig build() { return new ClientConfig(this); }
    }
}

