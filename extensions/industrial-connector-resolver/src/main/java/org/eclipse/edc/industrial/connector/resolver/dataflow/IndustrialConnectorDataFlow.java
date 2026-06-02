package org.eclipse.edc.industrial.connector.resolver.dataflow;

import org.eclipse.edc.common.spi.config.IndustrialConnectorResolverConfigService;
import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.common.spi.pki.PkiCertificateService;
import org.eclipse.edc.common.spi.security.SecurityService;
import org.eclipse.edc.common.spi.security.mosquitto.MosquittoCredentials;
import org.eclipse.edc.common.spi.security.mosquitto.MosquittoSecurityRequest;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.industrial.connector.resolver.datatypes.IndustrialConnectorDataTypes;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static org.eclipse.edc.common.spi.helpers.Helpers.firstNonBlank;

public class IndustrialConnectorDataFlow implements DataFlowController {
    private static final String EDC_NAMESPACE = "https://w3id.org/edc/v0.0.1/ns/";

    private final PkiCertificateService certificateService;
    private final IndustrialConnectorResolverConfigService configService;
    private final IndustrialConnectorDataTypes industrialConnectorDataTypes;
    private final SecurityService securityService;
    private TransferFlowService transferFlowService;

    public IndustrialConnectorDataFlow(PkiCertificateService pkiCertificateService,
                                       IndustrialConnectorDataTypes industrialConnectorDataTypes,
                                       IndustrialConnectorResolverConfigService configService,
                                       SecurityService securityService) {
        this.certificateService = pkiCertificateService;
        this.configService = configService;
        this.industrialConnectorDataTypes = industrialConnectorDataTypes;
        this.securityService = securityService;
    }

    public void setDataFlow(TransferFlowService transferFlowService) {
        this.transferFlowService = transferFlowService;
    }

    @Override
    public boolean canHandle(TransferProcess transferProcess) {
        if (transferFlowService != null && transferFlowService.canHandle(transferProcess)) {
            return true;
        }
        var contentDataAddress = transferProcess.getContentDataAddress();
        if (contentDataAddress == null || industrialConnectorDataTypes == null) {
            return false;
        }

        // Handle defined data types only
        return industrialConnectorDataTypes.isSupported(contentDataAddress.getType());
    }

    @Override
    public org.eclipse.edc.spi.response.StatusResult<Void> suspend(TransferProcess transferProcess) {
        return null;
    }

    @Override
    public org.eclipse.edc.spi.response.StatusResult<Void> terminate(TransferProcess transferProcess) {
        return null;
    }

    @Override
    public Set<String> transferTypesFor(org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset asset) {
        return industrialConnectorDataTypes.getSupportedDataTypes();
    }

    @Override
    public org.eclipse.edc.spi.response.@NotNull StatusResult<DataFlowResponse> start(TransferProcess transferProcess, org.eclipse.edc.policy.model.Policy policy) {
        var contentDataAddress = transferProcess.getContentDataAddress();
        if (contentDataAddress == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No content data address available");
        }

        String transferType = transferProcess.getTransferType();

        if ("MQTT-PUSH".equalsIgnoreCase(transferType)) {
            return handlePushTransfer(transferProcess);
        } else {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Unsupported transfer type: " + transferType);
        }
    }

    private StatusResult<DataFlowResponse> handlePushTransfer(TransferProcess transferProcess) {
        // Get the assetId from the transfer process
        String assetId = transferProcess.getAssetId();
        if (assetId == null) {
            return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No asset ID available for push transfer");
        }

        var transferId = transferProcess.getId();
        var transferType = transferProcess.getTransferType();

        var config = configService.getConfig();
        var isCertificateBasedAuthentication = config.getCertBasedAuthenticationEnabled();

        // Create user and permissions using Mosquitto Dynamic Security
        // Topic pattern for this asset (allow wildcard subscriptions)
        String topicPattern = assetId + "/#";

        var existingDataAddress = transferProcess.getContentDataAddress();
        //copyng existing data address and adding certificate and ca-chain
        String serverUrl = firstNonBlank(
                existingDataAddress.getStringProperty("serverUrl"),
                existingDataAddress.getStringProperty(EDC_NAMESPACE + "serverUrl")
        );

        String nodeIdSpec = firstNonBlank(
                existingDataAddress.getStringProperty("nodeId"),
                existingDataAddress.getStringProperty(EDC_NAMESPACE + "nodeId"),
                existingDataAddress.getStringProperty("nodeIds"),
                existingDataAddress.getStringProperty(EDC_NAMESPACE + "nodeIds")
        );

        if (isCertificateBasedAuthentication && this.certificateService != null) {
            var csr = transferProcess.getDataDestination().getStringProperty("csr");

            if (csr == null) {
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, "No CSR provided for certificate-based authentication");
            }

            var username = certificateService.getCommonName(csr).getContent();

            if (username == null) {
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Failed to extract username from CSR");
            }

            // Use the injected SecurityService to provision access
            var securityRequest = new MosquittoSecurityRequest();
            securityRequest.setUserName(username);
            securityRequest.setTopic(topicPattern);
            securityRequest.setTransferId(transferId);
            Result<MosquittoCredentials> credentialsResult = securityService.provisionAccess(securityRequest);
            var credentials = credentialsResult.getContent();
            var signedCertificate = certificateService.requestCertificate(csr, credentials.getUsername(), 365);
            var caChain = certificateService.getCertificateChain();

            var dataAddress = DataAddress.Builder.newInstance()
                    .type(transferType)
                    .property("endpoint", config.getSinkServiceUrl())
                    .property("topic", assetId)
                    .property("username", credentials.getUsername())
                    .property("certificate", signedCertificate.getContent())
                    .property("ca-chain", caChain.getContent())
                    .property("serverUrl", serverUrl)
                    .property("nodeIds", nodeIdSpec)
                    .build();

            transferProcess.setContentDataAddress(dataAddress);

            return getDataFlowResponseStatusResult(transferProcess);

        } else {
            var securityRequest = new MosquittoSecurityRequest();
            securityRequest.setTopic(topicPattern);
            securityRequest.setTransferId(transferId);
            Result<MosquittoCredentials> credentialsResult = securityService.provisionAccess(securityRequest);
            var credentials = credentialsResult.getContent();

            // Return success response with MQTT broker details for EDR
            // The DataFlowResponse contains the DataAddress that will be returned to consumer
            var dataAddress = DataAddress.Builder.newInstance()
                    .type(transferType)
                    .property("endpoint", config.getSinkServiceUrl())
                    .property("topic", assetId)
                    .property("username", credentials.getUsername())
                    .property("password", credentials.getPassword())
                    .property("serverUrl", serverUrl)
                    .property("nodeIds", nodeIdSpec)
                    .build();

            transferProcess.setContentDataAddress(dataAddress);

            return getDataFlowResponseStatusResult(transferProcess);
        }
    }

    @NotNull
    private StatusResult<DataFlowResponse> getDataFlowResponseStatusResult(TransferProcess transferProcess) {

        if (transferFlowService != null) {
            var success = transferFlowService.startTransfer(transferProcess);
            if (success.failed()) {
                transferFlowService.terminateTransfer(transferProcess);
                return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Failed to start transfer: " + success.getContent());
            }
            return success;
        }

        return StatusResult.failure(ResponseStatus.FATAL_ERROR, "Failed to start transfer: Transfer flow not definied.");
    }
}
