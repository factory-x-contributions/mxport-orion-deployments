package org.eclipse.edc.industrial.local.dataflow;
import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.common.spi.security.SecurityService;
import org.eclipse.edc.common.spi.security.mosquitto.MosquittoSecurityRequest;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.industrial.local.dataflow.mqttpush.IndustrialConnectorLocalPushService;
import org.eclipse.edc.industrial.local.dataflow.mqttpush.MqttBrokerConfig;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Industrial Connector Local implementation of the transfer flow service.
 * Handles the lifecycle of OPC-UA MQTT data transfers including provisioning,
 * execution, and cleanup of MQTT broker credentials and permissions.
 */
public class IndustrialConnectorLocalTransferServiceImpl implements TransferFlowService {

    private static final String OPCUAMQTT_TYPE = "opcuamqtt";
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String MQTT_PUSH_TYPE = "MQTT-PUSH";

    private final IndustrialConnectorLocalPushService opcUaPushService;
    private final MqttBrokerConfig brokerConfig;
    private final SecurityService securityService;
    private final Monitor monitor;

    public IndustrialConnectorLocalTransferServiceImpl(IndustrialConnectorLocalPushService opcUaPushService,
                                                 MqttBrokerConfig brokerConfig,
                                                 SecurityService securityService,
                                                 Monitor monitor) {
        this.opcUaPushService = opcUaPushService;
        this.brokerConfig = brokerConfig;
        this.securityService = securityService;
        this.monitor = monitor;
    }


    @Override
    public boolean canHandle(@NotNull TransferProcess transferProcess) {
        var contentDataAddress = transferProcess.getContentDataAddress();
        if (contentDataAddress == null) {
            return false;
        }

        boolean isOpcUaMqttSource = OPCUAMQTT_TYPE.equalsIgnoreCase(contentDataAddress.getType());
        String transferType = transferProcess.getTransferType();

        boolean canHandle = isOpcUaMqttSource && (transferType == null || MQTT_PUSH_TYPE.equalsIgnoreCase(transferType));
        monitor.debug(() -> "Can handle transfer " + transferProcess.getId() + ": " + canHandle);
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

        if (MQTT_PUSH_TYPE.equalsIgnoreCase(transferType) || transferType == null) {
            return handlePushTransfer(transferProcess);
        } else {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Unsupported transfer type: " + transferType);
        }
    }

    @Override
    @NotNull
    public StatusResult<Void> suspendTransfer(@NotNull TransferProcess transferProcess) {
        return StatusResult.success();
    }

    @Override
    @NotNull
    public StatusResult<Void> terminateTransfer(@NotNull TransferProcess transferProcess) {
        String transferId = transferProcess.getId();
        opcUaPushService.stopPushing(transferId);

        String username = "edc-user-" + transferId;
        String roleName = "edc-role-" + transferId;
        monitor.info("Cleaning up Mosquitto user " + username + " and role " + roleName);

        var revokeRequest = new MosquittoSecurityRequest();
        revokeRequest.setUserName(username);
        revokeRequest.setRoleName(roleName);
        revokeRequest.setTopic(transferProcess.getAssetId());

        var cleanupResult = securityService.revokeAccess(revokeRequest);
        if (cleanupResult.failed()) {
            monitor.warning("Failed to clean up Mosquitto user and role: " + cleanupResult.getFailureDetail());
        } else {
            monitor.info("Cleaned up Mosquitto user " + username + " and role " + roleName);
        }

        monitor.info("Removed MQTT EDR for transfer " + transferId);
        return StatusResult.success();
    }

    @Override
    @NotNull
    public Set<String> getSupportedTransferTypes(@NotNull Asset asset) {
        return Set.of(OPCUAMQTT_TYPE);
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

        return String.format(
                "{\"type\":\"opcua_read_request\",\"transferId\":\"%s\",\"opcuaServer\":\"%s\",\"nodeIds\":[\"%s\"],\"mqttBroker\":\"%s\",\"mqttTopic\":\"%s\",\"pushInterval\":%s,\"timestamp\":\"%s\"}",
                transferId, serverUrl, nodeIdSpec, brokerUrl, assetId, pushInterval, java.time.Instant.now().toString()
        );
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
     * Handles MQTT-PUSH transfer by starting the push service and provisioning credentials.
     *
     * @param transferProcess the transfer process
     * @return a status result containing the data flow response with MQTT broker details
     */
    private StatusResult<DataFlowResponse> handlePushTransfer(@NotNull TransferProcess transferProcess) {
        String assetId = transferProcess.getAssetId();
        if (assetId == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No asset ID available for push transfer");
        }

        String transferId = transferProcess.getId();
        boolean isCertificateBasedAuthentication = brokerConfig.isCertificateAuth();

        String brokerUrl = brokerConfig != null && brokerConfig.isConfigured()
                ? brokerConfig.getBrokerUrl()
                : "tcp://localhost:1883";

        opcUaPushService.startPushing(transferId, assetId, transferProcess.getContentDataAddress());

        String authToken = UUID.randomUUID().toString();
        monitor.info("Stored MQTT EDR for transfer " + transferId + " - Topic: " + assetId + ", Broker: " + brokerUrl);

        if (isCertificateBasedAuthentication) {
            var username = transferProcess.getContentDataAddress().getStringProperty("username");
            var caChain = transferProcess.getContentDataAddress().getStringProperty("ca-chain");
            var certificate = transferProcess.getContentDataAddress().getStringProperty("certificate");
            var topic = transferProcess.getContentDataAddress().getStringProperty("topic");

            return handleCertificateAuthentication(username, caChain, certificate, topic, brokerUrl, authToken);
        } else {
            var topic = transferProcess.getContentDataAddress().getStringProperty("topic");
            var username = transferProcess.getContentDataAddress().getStringProperty("username");
            var password = transferProcess.getContentDataAddress().getStringProperty("password");

            return handlePasswordAuthentication(username, password, topic, brokerUrl, authToken);
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
                .type(OPCUAMQTT_TYPE)
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
                .type(OPCUAMQTT_TYPE)
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
}
