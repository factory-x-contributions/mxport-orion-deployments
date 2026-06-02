package org.eclipse.edc.industrial.wss.dataflow;

import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.industrial.wss.server.IndustrialWebSocketService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket-based implementation of the transfer flow service.
 * Handles the lifecycle of WebSocket data transfers by sending commands
 * to connected WebSocket clients that will push data to MQTT broker.
 */
public class IndustrialConnectorWssTransferFlowImpl implements TransferFlowService {

    private static final String WSS_TYPE = "wss";
    private static final String OPCUAMQTT_TYPE = "opcuamqtt";
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String MQTT_PUSH_TYPE = "MQTT-PUSH";

    private final IndustrialWebSocketService webSocketService;
    private final Monitor monitor;
    private final Map<String, String> activeTransfers; // transferId -> clientId mapping

    public IndustrialConnectorWssTransferFlowImpl(Monitor monitor,
                                                  IndustrialWebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.monitor = monitor;
        this.activeTransfers = new ConcurrentHashMap<>();
    }

    @Override
    public boolean canHandle(@NotNull TransferProcess transferProcess) {
        var contentDataAddress = transferProcess.getContentDataAddress();
        if (contentDataAddress == null) {
            return false;
        }

        // Support both wss and opcuamqtt types when using MQTT-PUSH transfer
        String dataType = contentDataAddress.getType();
        boolean isSupportedType = WSS_TYPE.equalsIgnoreCase(dataType) || 
                                 OPCUAMQTT_TYPE.equalsIgnoreCase(dataType);
        String transferType = transferProcess.getTransferType();

        boolean canHandle = isSupportedType && MQTT_PUSH_TYPE.equalsIgnoreCase(transferType);
        monitor.debug(() -> "WebSocket TransferFlow canHandle transfer " + 
                     transferProcess.getId() + ": " + canHandle);
        return canHandle;
    }

    @Override
    @NotNull
    public StatusResult<DataFlowResponse> startTransfer(@NotNull TransferProcess transferProcess) {
        var contentDataAddress = transferProcess.getContentDataAddress();
        if (contentDataAddress == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No content data address available");
        }

        String transferType = transferProcess.getTransferType();

        if (MQTT_PUSH_TYPE.equalsIgnoreCase(transferType)) {
            return handlePushTransfer(transferProcess);
        } else {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, 
                "Unsupported transfer type: " + transferType);
        }
    }

    @Override
    @NotNull
    public StatusResult<Void> suspendTransfer(@NotNull TransferProcess transferProcess) {
        String transferId = transferProcess.getId();
        
        // Send suspend command to the client
        String clientId = activeTransfers.get(transferId);
        if (clientId != null) {
            String suspendCommand = buildSuspendCommand(transferId);
            boolean sent = webSocketService.sendToClient(clientId, suspendCommand);
            
            if (sent) {
                monitor.info("Sent suspend command for transfer " + transferId + " to client " + clientId);
                return StatusResult.success();
            } else {
                monitor.warning("Failed to send suspend command for transfer " + transferId);
                return StatusResult.failure(ResponseStatus.ERROR_RETRY, 
                    "Failed to send suspend command to client");
            }
        }
        
        return StatusResult.success();
    }

    @Override
    @NotNull
    public StatusResult<Void> terminateTransfer(@NotNull TransferProcess transferProcess) {
        String transferId = transferProcess.getId();
        String clientId = activeTransfers.remove(transferId);
        
        if (clientId != null) {
            // Send termination command to the client
            String terminateCommand = buildTerminateCommand(transferId);
            boolean sent = webSocketService.sendToClient(clientId, terminateCommand);
            
            if (sent) {
                monitor.info("Sent terminate command for transfer " + transferId + " to client " + clientId);
            } else {
                monitor.warning("Failed to send terminate command for transfer " + transferId);
            }
        }
        
        monitor.info("Removed transfer " + transferId + " from active transfers");
        return StatusResult.success();
    }

    @Override
    @NotNull
    public Set<String> getSupportedTransferTypes(@NotNull Asset asset) {
        return Set.of(WSS_TYPE, OPCUAMQTT_TYPE);
    }

    @Override
    @NotNull
    public String buildReadCommand(@NotNull String transferId,
                                   @NotNull DataAddress contentDataAddress,
                                   @NotNull String assetId,
                                   @NotNull String brokerUrl) {
        String serverUrl = firstNonBlank(
                contentDataAddress.getStringProperty("serverUrl"),
                contentDataAddress.getStringProperty(EDC_NAMESPACE + "serverUrl")
        );

        String nodeIdSpec = firstNonBlank(
                contentDataAddress.getStringProperty("nodeId"),
                contentDataAddress.getStringProperty(EDC_NAMESPACE + "nodeId"),
                contentDataAddress.getStringProperty("nodeIds"),
                contentDataAddress.getStringProperty(EDC_NAMESPACE + "nodeIds")
        );
        
        String pushInterval = getProperty(contentDataAddress, "pushInterval", "5000");

        // Build JSON command with all necessary details
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("{");
        commandBuilder.append("\"type\":\"opcua_read_request\",");
        commandBuilder.append("\"transferId\":\"").append(transferId).append("\",");
        commandBuilder.append("\"nodeIds\":[\"").append(nodeIdSpec).append("\"],");
        commandBuilder.append("\"mqttTopic\":\"").append(assetId).append("\",");
        commandBuilder.append("\"pushInterval\":").append(pushInterval).append(",");

        commandBuilder.append("\"timestamp\":\"").append(java.time.Instant.now().toString()).append("\"");
        commandBuilder.append("}");

        return commandBuilder.toString();
    }

    @Override
    @NotNull
    public String getProperty(@NotNull DataAddress dataAddress,
                              @NotNull String key,
                              @NotNull String defaultValue) {
        String value = dataAddress.getStringProperty(key);
        if (value == null) {
            value = dataAddress.getStringProperty(EDC_NAMESPACE + key);
        }
        return value != null ? value : defaultValue;
    }

    /**
     * Handles MQTT-PUSH transfer by sending a command via WebSocket to the client.
     *
     * @param transferProcess the transfer process
     * @return a status result containing the data flow response with MQTT broker details
     */
    private StatusResult<DataFlowResponse> handlePushTransfer(@NotNull TransferProcess transferProcess) {
        String assetId = transferProcess.getAssetId();
        if (assetId == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, 
                "No asset ID available for push transfer");
        }

        String transferId = transferProcess.getId();
        var contentDataAddress = transferProcess.getContentDataAddress();

        // Extract broker URL from content data address
        String brokerUrl = firstNonBlank(
                contentDataAddress.getStringProperty("endpoint"),
                contentDataAddress.getStringProperty(EDC_NAMESPACE + "endpoint"),
                "tcp://localhost:1883"
        );

        // Build the read command with all necessary details
        String readCommand = buildReadCommand(transferId, contentDataAddress, assetId, brokerUrl);

        // Get authentication type to determine which client to send to
        boolean isCertificateAuth = contentDataAddress.getStringProperty("certificate") != null ||
                                   contentDataAddress.getStringProperty(EDC_NAMESPACE + "certificate") != null;

        // Broadcast the command to all connected WebSocket clients
        // In a production scenario, you might want to target a specific client
        String clientId = selectTargetClient(transferProcess);
        
        if (clientId != null && webSocketService.hasSession(clientId)) {
            boolean sent = webSocketService.sendToClient(clientId, readCommand);
            if (!sent) {
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, 
                    "Failed to send command to WebSocket client " + clientId);
            }
            activeTransfers.put(transferId, clientId);
            monitor.info("Sent transfer command for " + transferId + " to client " + clientId);
        } else {
            // Broadcast to all clients if no specific client found
            webSocketService.broadcast(readCommand);
            monitor.info("Broadcast transfer command for " + transferId + " to all connected clients");
        }

        String authToken = UUID.randomUUID().toString();
        monitor.info("Initiated WebSocket transfer " + transferId + 
                    " - Topic: " + assetId + ", Broker: " + brokerUrl);

        // Return the data flow response with authentication details
        if (isCertificateAuth) {
            var username = contentDataAddress.getStringProperty("username");
            var caChain = contentDataAddress.getStringProperty("ca-chain");
            var certificate = contentDataAddress.getStringProperty("certificate");
            var topic = contentDataAddress.getStringProperty("topic");

            if (username == null) {
                username = contentDataAddress.getStringProperty(EDC_NAMESPACE + "username");
            }
            if (caChain == null) {
                caChain = contentDataAddress.getStringProperty(EDC_NAMESPACE + "ca-chain");
            }
            if (certificate == null) {
                certificate = contentDataAddress.getStringProperty(EDC_NAMESPACE + "certificate");
            }
            if (topic == null) {
                topic = contentDataAddress.getStringProperty(EDC_NAMESPACE + "topic");
            }

            return handleCertificateAuthentication(username, caChain, certificate, 
                                                  topic != null ? topic : assetId, 
                                                  brokerUrl, authToken);
        } else {
            var topic = contentDataAddress.getStringProperty("topic");
            var username = contentDataAddress.getStringProperty("username");
            var password = contentDataAddress.getStringProperty("password");

            if (topic == null) {
                topic = contentDataAddress.getStringProperty(EDC_NAMESPACE + "topic");
            }
            if (username == null) {
                username = contentDataAddress.getStringProperty(EDC_NAMESPACE + "username");
            }
            if (password == null) {
                password = contentDataAddress.getStringProperty(EDC_NAMESPACE + "password");
            }

            return handlePasswordAuthentication(username, password, 
                                               topic != null ? topic : assetId, 
                                               brokerUrl, authToken);
        }
    }

    /**
     * Handles certificate-based MQTT authentication flow.
     */
    private StatusResult<DataFlowResponse> handleCertificateAuthentication(
            String username,
            String caChain,
            String certificate,
            String topicPattern,
            String brokerUrl,
            String authToken) {

        var dataAddress = DataAddress.Builder.newInstance()
                .type(WSS_TYPE)
                .property(EDC_NAMESPACE + "endpoint", brokerUrl)
                .property(EDC_NAMESPACE + "authToken", authToken)
                .property(EDC_NAMESPACE + "topic", topicPattern)
                .property(EDC_NAMESPACE + "username", username)
                .property(EDC_NAMESPACE + "certificate", certificate)
                .property(EDC_NAMESPACE + "ca-chain", caChain)
                .build();

        var response = DataFlowResponse.Builder.newInstance()
                .dataAddress(dataAddress)
                .build();

        return StatusResult.success(response);
    }

    /**
     * Handles password-based MQTT authentication flow.
     */
    private StatusResult<DataFlowResponse> handlePasswordAuthentication(
            String username,
            String password,
            String topicPattern,
            String brokerUrl,
            String authToken) {

        var dataAddress = DataAddress.Builder.newInstance()
                .type(WSS_TYPE)
                .property(EDC_NAMESPACE + "endpoint", brokerUrl)
                .property(EDC_NAMESPACE + "authToken", authToken)
                .property(EDC_NAMESPACE + "topic", topicPattern)
                .property(EDC_NAMESPACE + "username", username)
                .property(EDC_NAMESPACE + "password", password)
                .build();

        var response = DataFlowResponse.Builder.newInstance()
                .dataAddress(dataAddress)
                .build();

        return StatusResult.success(response);
    }

    /**
     * Selects the target client for a transfer.
     * This is a simple implementation that can be extended with more sophisticated logic.
     */
    private String selectTargetClient(TransferProcess transferProcess) {
        // Try to get a preferred client ID from the transfer process data destination
        var dataDestination = transferProcess.getDataDestination();
        if (dataDestination != null) {
            String clientId = dataDestination.getStringProperty("clientId");
            if (clientId != null && webSocketService.hasSession(clientId)) {
                return clientId;
            }
        }

        // Otherwise, return the first available client
        var activeClients = webSocketService.getActiveClientIds();
        if (!activeClients.isEmpty()) {
            return activeClients.iterator().next();
        }

        return null;
    }

    /**
     * Builds a suspend command for a transfer.
     */
    private String buildSuspendCommand(String transferId) {
        return String.format(
                "{\"type\":\"suspend_transfer\",\"transferId\":\"%s\",\"timestamp\":\"%s\"}",
                transferId, java.time.Instant.now().toString()
        );
    }

    /**
     * Builds a terminate command for a transfer.
     */
    private String buildTerminateCommand(String transferId) {
        return String.format(
                "{\"type\":\"terminate_transfer\",\"transferId\":\"%s\",\"timestamp\":\"%s\"}",
                transferId, java.time.Instant.now().toString()
        );
    }

    /**
     * Returns the first non-blank value from the given values.
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * Escapes special characters in JSON strings.
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
