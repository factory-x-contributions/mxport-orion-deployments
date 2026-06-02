package org.eclipse.edc.industrial.local.dataflow;

import org.eclipse.edc.common.spi.config.IndustrialConnectorResolverConfigImpl;
import org.eclipse.edc.common.spi.config.IndustrialConnectorResolverConfigService;
import org.eclipse.edc.common.spi.dataflow.TransferFlowProvisionerService;
import org.eclipse.edc.common.spi.dataflow.TransferFlowService;
import org.eclipse.edc.common.spi.mqttclient.MqttClient;
import org.eclipse.edc.common.spi.mqttclient.PahoMqttClientImpl;
import org.eclipse.edc.common.spi.security.SecurityService;
import org.eclipse.edc.common.spi.security.mosquitto.MosquittoCredentials;
import org.eclipse.edc.common.spi.security.mosquitto.MosquittoSecurityRequest;
import org.eclipse.edc.common.spi.security.mosquitto.MqttSecurityServiceImpl;
import org.eclipse.edc.industrial.local.dataflow.mqttpush.IndustrialConnectorLocalPushService;
import org.eclipse.edc.industrial.local.dataflow.mqttpush.MqttBrokerConfig;
import org.eclipse.edc.industrial.local.dataflow.mqttpush.OpcUaMqttPushServiceImpl;
import org.eclipse.edc.industrial.local.dataflow.opcua.OpcUaClientService;
import org.eclipse.edc.industrial.local.dataflow.opcua.OpcUaClientServiceImpl;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Provisioner service for OPC UA MQTT transfers with Mosquitto broker.
 *
 * This service creates and configures the necessary MQTT and OPC UA clients
 * based on the provided configuration, handling both certificate-based and
 * credential-based authentication mechanisms.
 */
public class IndustrialConnectorLocalTransferFlowProvisionerServiceImpl implements TransferFlowProvisionerService {

    private final TransferFlowService transferFlowService;

    /**
     * Creates an IndustrialConnectorLocalTransferFlowProvisionerServiceImpl that instantiates
     * the appropriate MQTT and OPC UA clients based on configuration.
     *
     * @param configService the industrial connector resolver configuration service
     * @param monitor for logging
     */
    public IndustrialConnectorLocalTransferFlowProvisionerServiceImpl(Monitor monitor,
                                                                IndustrialConnectorResolverConfigService<?> configService) {
        IndustrialConnectorResolverConfigImpl config = configService.getConfig();
        
        // Create MQTT client for push credentials
        MqttClient pushMqttClient = createPushMqttClient(monitor, config);
        
        // Create OPC UA client
        OpcUaClientService opcUaClient = new OpcUaClientServiceImpl(monitor);
        
        // Create MQTT broker configuration
        MqttBrokerConfig brokerConfig = createMqttBrokerConfig(config);
        
        // Create OPC UA MQTT push service
        IndustrialConnectorLocalPushService opcUaPushService = new OpcUaMqttPushServiceImpl(
                opcUaClient,
                pushMqttClient,
                brokerConfig,
                monitor
        );
        
        // Create security service for MQTT access control
        String brokerUrl = config.getSinkServiceUrl();
        SecurityService<MosquittoCredentials, MosquittoSecurityRequest> securityService = new MqttSecurityServiceImpl(pushMqttClient, monitor);
        
        // Create and store the transfer flow service
        this.transferFlowService = new IndustrialConnectorLocalTransferServiceImpl(
                opcUaPushService,
                brokerConfig,
                (SecurityService) securityService,
                monitor
        );
        
        monitor.info("Industrial Connector Local Transfer Flow Provisioner initialized with broker: " + brokerConfig.getBrokerUrl());
    }

    /**
     * Creates the appropriate MQTT client for push credentials based on the configuration.
     * If certificate-based authentication is enabled, creates a cert-based client.
     * Otherwise, creates a credential-based client with push user credentials.
     *
     * @param monitor for logging
     * @param config the configuration containing auth details
     * @return an MQTT client configured with either certificates or credentials for push
     */
    private static MqttClient createPushMqttClient(Monitor monitor, IndustrialConnectorResolverConfigImpl config) {
        String brokerUrl = config.getSinkServiceUrl();

        // Check if certificate-based authentication is enabled
        if (config.getCertBasedAuthenticationEnabled() != null && config.getCertBasedAuthenticationEnabled()) {
            // Certificate-based authentication - use push user certificates
            String caCertPath = config.getSinkServiceCaChainCertificatePath();
            String pushUserCertPath = config.getSinkServicePushUserCertificatePath();
            String pushUserKeyPath = config.getSinkServicePushUserCertificateKeyPath();

            monitor.info("Creating MQTT client with certificate-based authentication for push user");
            monitor.debug("CA Cert: " + caCertPath + ", Push User Cert: " + pushUserCertPath + ", Push User Key: " + pushUserKeyPath);

            return new PahoMqttClientImpl(
                    monitor,
                    brokerUrl,
                    caCertPath,
                    pushUserCertPath,
                    pushUserKeyPath
            );
        } else {
            // Credential-based authentication with push user username/password
            String pushUsername = config.getSinkServicePushUsername();
            String pushPassword = config.getSinkServicePushPassword();

            monitor.info("Creating MQTT client with credential-based authentication (push user)");
            monitor.debug("Push Username: " + pushUsername);

            return new PahoMqttClientImpl(
                    monitor,
                    brokerUrl,
                    pushUsername,
                    pushPassword
            );
        }
    }

    /**
     * Creates MQTT broker configuration based on authentication type.
     *
     * @param config the configuration containing broker details
     * @return an MQTT broker configuration object
     */
    private static MqttBrokerConfig createMqttBrokerConfig(IndustrialConnectorResolverConfigImpl config) {
        String brokerUrl = config.getSinkServiceUrl();
        
        if (config.getCertBasedAuthenticationEnabled() != null && config.getCertBasedAuthenticationEnabled()) {
            String caCertPath = config.getSinkServiceCaChainCertificatePath();
            String pushUserCertPath = config.getSinkServicePushUserCertificatePath();
            String pushUserKeyPath = config.getSinkServicePushUserCertificateKeyPath();
            
            return new MqttBrokerConfig(brokerUrl, caCertPath, pushUserCertPath, pushUserKeyPath, null);
        } else {
            String pushUsername = config.getSinkServicePushUsername();
            String pushPassword = config.getSinkServicePushPassword();
            
            return new MqttBrokerConfig(brokerUrl, pushUsername, pushPassword);
        }
    }

    @Override
    public TransferFlowService getTransferFlowService() {
        return transferFlowService;
    }
}
