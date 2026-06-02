package org.eclipse.edc.common.spi.config.mosquitto;

import org.eclipse.edc.common.spi.config.IndustrialConnectorResolverConfigImpl;
import org.eclipse.edc.common.spi.config.IndustrialConnectorResolverConfigService;
import org.eclipse.edc.spi.system.configuration.Config;

public class IndustrialConnectorResolverConfigServiceImpl implements IndustrialConnectorResolverConfigService<IndustrialConnectorResolverConfigImpl> {

    // Configuration keys - these should be defined as constants in the extension
    private static final String CERTIFICATE_AUTHENTICATION_ENABLED = "edc.industrial.connector.cert.auth.enabled";
    private static final String MQTT_BROKER_URL_ENV = "edc.opcua.mqtt.broker.url";
    private static final String MQTT_USERNAME_ENV = "edc.opcua.mqtt.username";
    private static final String MQTT_PASSWORD_ENV = "edc.opcua.mqtt.password";
    private static final String MQTT_ADMIN_USERNAME_ENV = "edc.opcua.mqtt.admin.username";
    private static final String MQTT_ADMIN_PASSWORD_ENV = "edc.opcua.mqtt.admin.password";
    private static final String MQTT_ADMIN_CERTIFICATE_PATH = "edc.opcua.mqtt.admin.cert.path";
    private static final String MQTT_ADMIN_CERTIFICATE_KEY_PATH = "edc.opcua.mqtt.admin.key.path";
    private static final String MQTT_PUSH_USER_CERTIFICATE_PATH = "edc.opcua.mqtt.push.user.cert.path";
    private static final String MQTT_PUSH_USER_CERTIFICATE_KEY_PATH = "edc.opcua.mqtt.push.user.key.path";
    private static final String MQTT_CA_CHAIN_CERTIFICATE_PATH = "edc.opcua.mqtt.ca.cert.path";
    private static final String PKI_ENDPOINT_URL = "edc.industrial.connector.pki.endpoint.url";
    private static final String PKI_ENDPOINT_KEY = "edc.industrial.connector.pki.endpoint.key";

    private final IndustrialConnectorResolverConfigImpl config;

    /**
     * Constructor that takes Config and reads configuration settings.
     *
     * @param config the Config object to read configuration from
     */
    public IndustrialConnectorResolverConfigServiceImpl(Config config) {
        var certBasedAuthEnabled = config.getBoolean(CERTIFICATE_AUTHENTICATION_ENABLED, false);
        var mqttBrokerUrl = config.getString(MQTT_BROKER_URL_ENV, null);
        var mqttUsername = config.getString(MQTT_USERNAME_ENV, null);
        var mqttPassword = config.getString(MQTT_PASSWORD_ENV, null);
        var adminUsername = config.getString(MQTT_ADMIN_USERNAME_ENV, null);
        var adminPassword = config.getString(MQTT_ADMIN_PASSWORD_ENV, null);
        var adminCertPath = config.getString(MQTT_ADMIN_CERTIFICATE_PATH, null);
        var adminKeyPath = config.getString(MQTT_ADMIN_CERTIFICATE_KEY_PATH, null);
        var pushUserCertPath = config.getString(MQTT_PUSH_USER_CERTIFICATE_PATH, null);
        var pushUserKeyPath = config.getString(MQTT_PUSH_USER_CERTIFICATE_KEY_PATH, null);
        var caChainCertPath = config.getString(MQTT_CA_CHAIN_CERTIFICATE_PATH, null);
        var pkiEndpointUrl = config.getString(PKI_ENDPOINT_URL, null);
        var pkiEndpointKey = config.getString(PKI_ENDPOINT_KEY, null);

        // Create the config object
        this.config = new IndustrialConnectorResolverConfigImpl();
        this.config.setCertBasedAuthenticationEnabled(certBasedAuthEnabled);
        this.config.setSinkServiceUrl(mqttBrokerUrl);
        this.config.setSinkServicePushUsername(mqttUsername);
        this.config.setSinkServicePushPassword(mqttPassword);
        this.config.setSinkServiceAdminUsername(adminUsername);
        this.config.setSinkServiceAdminPassword(adminPassword);
        this.config.setSinkServiceAdminCertificatePath(adminCertPath);
        this.config.setSinkServiceAdminCertificateKeyPath(adminKeyPath);
        this.config.setSinkServicePushUserCertificatePath(pushUserCertPath);
        this.config.setSinkServicePushUserCertificateKeyPath(pushUserKeyPath);
        this.config.setSinkServiceCaChainCertificatePath(caChainCertPath);
        this.config.setPkiEndpointUrl(pkiEndpointUrl);
        this.config.setPkiEndpointKey(pkiEndpointKey);
    }

    @Override
    public IndustrialConnectorResolverConfigImpl getConfig() {
        return this.config;
    }
}