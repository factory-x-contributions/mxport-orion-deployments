package edc.industrial.connector.wss.client.opcua;

/**
 * Service interface for reading values from an OPC-UA server.
 */
public interface OpcUaClientService {

    /**
     * Reads a single value from an OPC-UA server node.
     *
     * @param endpoint OPC-UA server endpoint URL (e.g., "opc.tcp://localhost:4840")
     * @param nodeId   OPC-UA node ID (e.g., "i=2259" or "ns=14;i=58250")
     * @return the value read from the node (may be null)
     * @throws Exception if connection or read fails
     */
    Object readValue(String endpoint, String nodeId) throws Exception;
}

