package edc.industrial.connector.wss.client.transfer;

import java.util.List;

/**
 * Represents a parsed transfer command received from the EDC WSS server.
 */
public class TransferCommand {

    public enum Type {
        /** Start reading OPC-UA data and push to MQTT. */
        OPCUA_READ_REQUEST,
        /** Suspend/pause an active transfer. */
        SUSPEND_TRANSFER,
        /** Terminate and remove an active transfer. */
        TERMINATE_TRANSFER,
        /** Unknown / unsupported message type. */
        UNKNOWN
    }

    private final Type type;

    // ---- Fields for OPCUA_READ_REQUEST ----
    private final String transferId;
    private final String opcuaServer;
    private final List<String> nodeIds;
    private final String mqttBroker;
    private final String mqttTopic;
    private final long pushIntervalMs;
    private final String authType;   // "password" | "certificate" | null
    private final String username;
    private final String password;
    private final String certificate;
    private final String caChain;

    private TransferCommand(Builder b) {
        this.type = b.type;
        this.transferId = b.transferId;
        this.opcuaServer = b.opcuaServer;
        this.nodeIds = b.nodeIds != null ? List.copyOf(b.nodeIds) : List.of();
        this.mqttBroker = b.mqttBroker;
        this.mqttTopic = b.mqttTopic;
        this.pushIntervalMs = b.pushIntervalMs;
        this.authType = b.authType;
        this.username = b.username;
        this.password = b.password;
        this.certificate = b.certificate;
        this.caChain = b.caChain;
    }

    public Type getType() { return type; }
    public String getTransferId() { return transferId; }
    public String getOpcuaServer() { return opcuaServer; }
    public List<String> getNodeIds() { return nodeIds; }
    public String getMqttBroker() { return mqttBroker; }
    public String getMqttTopic() { return mqttTopic; }
    public long getPushIntervalMs() { return pushIntervalMs; }
    public String getAuthType() { return authType; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getCertificate() { return certificate; }
    public String getCaChain() { return caChain; }

    public boolean isCertificateAuth() {
        return "certificate".equalsIgnoreCase(authType);
    }

    @Override
    public String toString() {
        return "TransferCommand{type=" + type + ", transferId='" + transferId + "', topic='" + mqttTopic + "'}";
    }

    public static Builder builder(Type type) {
        return new Builder(type);
    }

    public static class Builder {
        private final Type type;
        private String transferId;
        private String opcuaServer;
        private List<String> nodeIds;
        private String mqttBroker;
        private String mqttTopic;
        private long pushIntervalMs = 5000;
        private String authType;
        private String username;
        private String password;
        private String certificate;
        private String caChain;

        private Builder(Type type) { this.type = type; }

        public Builder transferId(String v) { this.transferId = v; return this; }
        public Builder opcuaServer(String v) { this.opcuaServer = v; return this; }
        public Builder nodeIds(List<String> v) { this.nodeIds = v; return this; }
        public Builder mqttBroker(String v) { this.mqttBroker = v; return this; }
        public Builder mqttTopic(String v) { this.mqttTopic = v; return this; }
        public Builder pushIntervalMs(long v) { this.pushIntervalMs = v; return this; }
        public Builder authType(String v) { this.authType = v; return this; }
        public Builder username(String v) { this.username = v; return this; }
        public Builder password(String v) { this.password = v; return this; }
        public Builder certificate(String v) { this.certificate = v; return this; }
        public Builder caChain(String v) { this.caChain = v; return this; }

        public TransferCommand build() { return new TransferCommand(this); }
    }
}

