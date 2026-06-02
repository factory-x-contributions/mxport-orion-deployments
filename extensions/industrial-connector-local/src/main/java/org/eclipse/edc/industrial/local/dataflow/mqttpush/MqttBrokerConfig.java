
package org.eclipse.edc.industrial.local.dataflow.mqttpush;

/**
 * Configuration for the provider-managed MQTT broker.
 * Supports both username/password and certificate-based authentication.
 * Retrieved from connector environment variables.
 */
public class MqttBrokerConfig {

    private final String brokerUrl;

    // Username/password authentication fields
    private final String username;
    private final String password;

    // Certificate-based authentication fields
    private final String caCertPath;
    private final String clientCertPath;
    private final String clientKeyPath;
    private final String clientKeyPassword;

    // Authentication type
    private final AuthenticationType authenticationType;

    public enum AuthenticationType {
        USERNAME_PASSWORD,
        CERTIFICATE,
        NONE
    }

    /**
     * Constructor for username/password authentication
     */
    public MqttBrokerConfig(String brokerUrl, String username, String password) {
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.caCertPath = null;
        this.clientCertPath = null;
        this.clientKeyPath = null;
        this.clientKeyPassword = null;
        this.authenticationType = (username != null && !username.trim().isEmpty())
                ? AuthenticationType.USERNAME_PASSWORD
                : AuthenticationType.NONE;
    }

    /**
     * Constructor for certificate-based authentication
     */
    public MqttBrokerConfig(String brokerUrl, String caCertPath, String clientCertPath, String clientKeyPath) {
        this(brokerUrl, caCertPath, clientCertPath, clientKeyPath, null);
    }

    /**
     * Constructor for certificate-based authentication with password-protected private key
     */
    public MqttBrokerConfig(String brokerUrl, String caCertPath, String clientCertPath,
                            String clientKeyPath, String clientKeyPassword) {
        this.brokerUrl = brokerUrl;
        this.username = null;
        this.password = null;
        this.caCertPath = caCertPath;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.clientKeyPassword = clientKeyPassword;
        this.authenticationType = (caCertPath != null && clientCertPath != null && clientKeyPath != null)
                ? AuthenticationType.CERTIFICATE
                : AuthenticationType.NONE;
    }

    /**
     * Factory method to create config based on available parameters
     */
    public static MqttBrokerConfig create(String brokerUrl, String username, String password,
                                          String caCertPath, String clientCertPath, String clientKeyPath,
                                          String clientKeyPassword) {
        // Prefer certificate authentication if available
        if (caCertPath != null && clientCertPath != null && clientKeyPath != null) {
            return new MqttBrokerConfig(brokerUrl, caCertPath, clientCertPath, clientKeyPath, clientKeyPassword);
        }
        // Fallback to username/password
        return new MqttBrokerConfig(brokerUrl, username, password);
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public String getClientKeyPassword() {
        return clientKeyPassword;
    }

    public AuthenticationType getAuthenticationType() {
        return authenticationType;
    }

    public boolean isConfigured() {
        return brokerUrl != null && !brokerUrl.trim().isEmpty();
    }

    public boolean isCertificateAuth() {
        return authenticationType == AuthenticationType.CERTIFICATE;
    }

    public boolean isUsernamePasswordAuth() {
        return authenticationType == AuthenticationType.USERNAME_PASSWORD;
    }

    public boolean hasAuthentication() {
        return authenticationType != AuthenticationType.NONE;
    }

    /**
     * Validates that the configuration is complete for the selected authentication type
     */
    public boolean isValid() {
        if (!isConfigured()) {
            return false;
        }

        switch (authenticationType) {
            case USERNAME_PASSWORD:
                return username != null && !username.trim().isEmpty() &&
                        password != null && !password.trim().isEmpty();
            case CERTIFICATE:
                return caCertPath != null && !caCertPath.trim().isEmpty() &&
                        clientCertPath != null && !clientCertPath.trim().isEmpty() &&
                        clientKeyPath != null && !clientKeyPath.trim().isEmpty();
            case NONE:
                return true; // Anonymous connection
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "MqttBrokerConfig{" +
                "brokerUrl='" + brokerUrl + '\'' +
                ", authenticationType=" + authenticationType +
                ", username='" + (username != null ? "***" : "null") + '\'' +
                ", password='" + (password != null ? "***" : "null") + '\'' +
                ", caCertPath='" + caCertPath + '\'' +
                ", clientCertPath='" + clientCertPath + '\'' +
                ", clientKeyPath='" + clientKeyPath + '\'' +
                ", clientKeyPassword='" + (clientKeyPassword != null ? "***" : "null") + '\'' +
                '}';
    }
}