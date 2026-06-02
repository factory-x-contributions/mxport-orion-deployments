package org.eclipse.edc.industrial.local.dataflow.opcua;

/**
 * Client interface for reading values from OPC UA servers.
 * This is a self-contained client specific to the industrial connector local extension.
 */
public interface OpcUaClientService {

    /**
     * Read a single value from an OPC UA server.
     *
     * @param endpoint OPC UA server endpoint URL (e.g., "opc.tcp://localhost:4840")
     * @param nodeId   OPC UA node ID (e.g., "i=2259" or "ns=14;i=58250")
     * @return The value read from the node
     * @throws Exception if connection or read fails
     */
    Object readValue(String endpoint, String nodeId) throws Exception;
}
