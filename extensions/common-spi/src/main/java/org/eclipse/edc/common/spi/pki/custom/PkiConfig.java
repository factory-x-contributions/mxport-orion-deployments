package org.eclipse.edc.common.spi.pki.custom;

/**
 * Configuration for PKI certificate service.
 * Retrieved from connector environment variables.
 */
public class PkiConfig {

    private final String endpoint;
    private final String apiKey;

    /**
     * Constructor for PKI configuration
     *
     * @param endpoint the PKI service endpoint URL
     * @param apiKey the API key for authentication
     */
    public PkiConfig(String endpoint, String apiKey) {
        this.endpoint = endpoint != null && endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
        this.apiKey = apiKey;
    }

    /**
     * Factory method to create config from environment settings
     */
    public static PkiConfig create(String endpoint, String apiKey) {
        return new PkiConfig(endpoint, apiKey);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Check if PKI configuration is available
     */
    public boolean isConfigured() {
        return endpoint != null && !endpoint.trim().isEmpty() &&
                apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Validates that the configuration is complete and valid
     */
    public boolean isValid() {
        if (!isConfigured()) {
            return false;
        }

        // Basic URL validation
        try {
            return endpoint.startsWith("http://") || endpoint.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the full URL for certificate requests
     */
    public String getCertificateRequestUrl() {
        return endpoint + "/api/Pki/certificates/request";
    }

    @Override
    public String toString() {
        return "PkiConfig{" +
                "endpoint='" + endpoint + '\'' +
                ", apiKey='" + (apiKey != null ? "***" : "null") + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PkiConfig pkiConfig = (PkiConfig) o;

        if (endpoint != null ? !endpoint.equals(pkiConfig.endpoint) : pkiConfig.endpoint != null) return false;
        return apiKey != null ? apiKey.equals(pkiConfig.apiKey) : pkiConfig.apiKey == null;
    }

    @Override
    public int hashCode() {
        int result = endpoint != null ? endpoint.hashCode() : 0;
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        return result;
    }
}