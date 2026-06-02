package org.eclipse.edc.common.spi.config;

import javax.naming.ConfigurationException;

/**
 * Configuration service interface for Industrial Connector Resolver.
 * <p>
 * This service provides access to configuration settings required for industrial data transfer operations,
 * including authentication methods, messaging service configurations, and PKI settings.
 * The service abstracts configuration details to support various messaging backends (MQTT, Kafka, etc.)
 * and authentication mechanisms (certificate-based, username/password).
 * </p>
 *
 * <h3>Configuration Categories:</h3>
 * <ul>
 *   <li><b>Authentication Settings:</b> Certificate-based vs credential-based authentication</li>
 *   <li><b>Messaging Service:</b> Broker URL, connection credentials, and certificates</li>
 *   <li><b>PKI Integration:</b> Public Key Infrastructure endpoint configuration</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Check authentication method
 * if (configService.isCertBasedAuthenticationEnabled()) {
 *     String adminCert = configService.getMqttAdminCertificatePath();
 *     // Use certificate-based authentication
 * } else {
 *     String username = configService.getMqttUsername();
 *     String password = configService.getMqttPassword();
 *     // Use username/password authentication
 * }
 * }</pre>
 *
 * @param <T> the configuration type that extends {@link IndustrialConnectorResolverConfigImpl}
 * @since 1.0.0
 * @see IndustrialConnectorResolverConfigImpl
 */
public interface IndustrialConnectorResolverConfigService<T extends IndustrialConnectorResolverConfigImpl> {

    /**
     * Retrieves the complete industrial resolver configuration.
     * <p>
     * This method returns the configuration object containing all settings required
     * for industrial connector operations, including messaging service configuration,
     * authentication settings, and PKI endpoints.
     * </p>
     *
     * @return the industrial resolver configuration object containing all settings
     * @throws ConfigurationException if the configuration cannot be loaded or is invalid
     * @see IndustrialConnectorResolverConfigImpl
     */
    IndustrialConnectorResolverConfigImpl getConfig();
}