package org.eclipse.edc.common.spi.mqttclient;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;


/**
 * MQTT client implementation using Eclipse Paho MQTT v3.
 * Manages connection lifecycle internally with connection pooling.
 * Supports both username/password and certificate-based authentication.
 */
public class PahoMqttClientImpl implements MqttClient {

    private final Monitor monitor;
    private final ConcurrentHashMap<String, org.eclipse.paho.client.mqttv3.MqttClient> clientCache = new ConcurrentHashMap<>();

    // Certificate authentication fields
    private final String caCertPath;
    private final String clientCertPath;
    private final String clientKeyPath;
    private final String clientKeyPassword;

    // Username/password authentication fields
    private final String username;
    private final String password;

    // MQTT broker URL
    private String brokerUrl;

    /**
     * Constructor for basic usage without pre-configured authentication
     */
    public PahoMqttClientImpl(Monitor monitor) {
        this.monitor = monitor;
        this.caCertPath = null;
        this.clientCertPath = null;
        this.clientKeyPath = null;
        this.clientKeyPassword = null;
        this.username = null;
        this.password = null;
        this.brokerUrl = null;
    }

    /**
     * Constructor for username/password authentication
     *
     * @param monitor Monitor for logging
     * @param username Username for MQTT broker authentication
     * @param password Password for MQTT broker authentication
     */
    public PahoMqttClientImpl(Monitor monitor, String brokerUrl, String username, String password) {
        this.monitor = monitor;
        this.caCertPath = null;
        this.clientCertPath = null;
        this.clientKeyPath = null;
        this.clientKeyPassword = null;
        this.username = username;
        this.password = password;
        this.brokerUrl = brokerUrl;
    }

    /**
     * Constructor for certificate-based authentication
     *
     * @param monitor Monitor for logging
     * @param caCertPath Path to CA certificate file (PEM format)
     * @param clientCertPath Path to client certificate file (PEM format)
     * @param clientKeyPath Path to client private key file (PEM format)
     */
    public PahoMqttClientImpl(Monitor monitor, String brokerUrl, String caCertPath, String clientCertPath, String clientKeyPath) {
        this.monitor = monitor;
        this.brokerUrl = brokerUrl;
        this.caCertPath = caCertPath;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.clientKeyPassword = null;
        this.username = null;
        this.password = null;
    }

    /**
     * Constructor for certificate-based authentication with password-protected private key
     *
     * @param monitor Monitor for logging
     * @param caCertPath Path to CA certificate file (PEM format)
     * @param clientCertPath Path to client certificate file (PEM format)
     * @param clientKeyPath Path to client private key file (PEM format)
     * @param clientKeyPassword Password for the private key (can be null)
     */
    public PahoMqttClientImpl(Monitor monitor, String brokerUrl, String caCertPath, String clientCertPath,
                              String clientKeyPath, String clientKeyPassword) {
        this.monitor = monitor;
        this.brokerUrl = brokerUrl;
        this.caCertPath = caCertPath;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.clientKeyPassword = clientKeyPassword;
        this.username = null;
        this.password = null;
    }


    @Override
    public void publish(String topic, byte[] payload, String username, String password) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic must not be null/blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }

        // Use provided credentials if available, otherwise fall back to instance credentials
        String effectiveUsername = (username != null) ? username : this.username;
        String effectivePassword = (password != null) ? password : this.password;

        org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, effectiveUsername, effectivePassword);

        try {
            org.eclipse.paho.client.mqttv3.MqttMessage mqttMessage = new org.eclipse.paho.client.mqttv3.MqttMessage(payload);
            mqttMessage.setQos(1); // At least once delivery
            mqttMessage.setRetained(false);

            client.publish(topic, mqttMessage);
            monitor.debug("Published to MQTT broker " + brokerUrl + " on topic '" + topic + "'");
        } catch (MqttException e) {
            monitor.severe("Failed to publish to MQTT broker " + brokerUrl + " on topic '" + topic + "'", e);
            throw e;
        }
    }

    /**
     * Publish method that uses stored credentials or certificate-based authentication
     */
    public void publish(String topic, byte[] payload) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic must not be null/blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }

        if (isCertificateAuthConfigured()) {
            // Use certificate-based authentication
            org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, null, null);

            try {
                org.eclipse.paho.client.mqttv3.MqttMessage mqttMessage = new org.eclipse.paho.client.mqttv3.MqttMessage(payload);
                mqttMessage.setQos(1); // At least once delivery
                mqttMessage.setRetained(false);

                client.publish(topic, mqttMessage);
                monitor.debug("Published to MQTT broker " + brokerUrl + " on topic '" + topic + "' using certificate auth");
            } catch (MqttException e) {
                monitor.severe("Failed to publish to MQTT broker " + brokerUrl + " on topic '" + topic + "' with certificate auth", e);
                throw e;
            }
        } else if (isUsernamePasswordConfigured()) {
            // Use stored username/password credentials
            publish(topic, payload, this.username, this.password);
        } else {
            // Use anonymous connection
            publish(topic, payload, null, null);
        }
    }

    @Override
    public void subscribe(String topic, String username, String password) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic must not be null/blank");
        }

        // Use provided credentials if available, otherwise fall back to instance credentials
        String effectiveUsername = (username != null) ? username : this.username;
        String effectivePassword = (password != null) ? password : this.password;

        org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, effectiveUsername, effectivePassword);

        try {
            client.subscribe(topic, 1); // QoS 1 for at least once delivery
            monitor.debug("Subscribed to MQTT broker " + brokerUrl + " on topic '" + topic + "'");
        } catch (MqttException e) {
            monitor.severe("Failed to subscribe to MQTT broker " + brokerUrl + " on topic '" + topic + "'", e);
            throw e;
        }
    }

    @Override
    public void subscribe(String topic) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic must not be null/blank");
        }

        if (isCertificateAuthConfigured()) {
            // Use certificate-based authentication
            org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, null, null);

            try {
                client.subscribe(topic, 1); // QoS 1 for at least once delivery
                monitor.debug("Subscribed to MQTT broker " + brokerUrl + " on topic '" + topic + "' using certificate auth");
            } catch (MqttException e) {
                monitor.severe("Failed to subscribe to MQTT broker " + brokerUrl + " on topic '" + topic + "' with certificate auth", e);
                throw e;
            }
        } else if (isUsernamePasswordConfigured()) {
            // Use stored username/password credentials
            subscribe(topic, this.username, this.password);
        } else {
            // Use anonymous connection
            subscribe(topic, null, null);
        }
    }

    @Override
    public void setCallback(org.eclipse.paho.client.mqttv3.MqttCallback callback, String username, String password) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }

        // Use provided credentials if available, otherwise fall back to instance credentials
        String effectiveUsername = (username != null) ? username : this.username;
        String effectivePassword = (password != null) ? password : this.password;

        org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, effectiveUsername, effectivePassword);

        try {
            client.setCallback(callback);
            monitor.debug("Set callback for MQTT broker " + brokerUrl);
        } catch (Exception e) {
            monitor.severe("Failed to set callback for MQTT broker " + brokerUrl, e);
            throw e;
        }
    }

    @Override
    public void setCallback(org.eclipse.paho.client.mqttv3.MqttCallback callback) throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback must not be null");
        }

        if (isCertificateAuthConfigured()) {
            // Use certificate-based authentication
            org.eclipse.paho.client.mqttv3.MqttClient client = getOrCreateClient(brokerUrl, null, null);

            try {
                client.setCallback(callback);
                monitor.debug("Set callback for MQTT broker " + brokerUrl + " using certificate auth");
            } catch (Exception e) {
                monitor.severe("Failed to set callback for MQTT broker " + brokerUrl + " with certificate auth", e);
                throw e;
            }
        } else if (isUsernamePasswordConfigured()) {
            // Use stored username/password credentials
            setCallback(callback, this.username, this.password);
        } else {
            // Use anonymous connection
            setCallback(callback, null, null);
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Broker URL must not be null/blank");
        }

        org.eclipse.paho.client.mqttv3.MqttClient client = clientCache.get(brokerUrl);
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    monitor.debug("Disconnected from MQTT broker: " + brokerUrl);
                }
                client.close();
                clientCache.remove(brokerUrl);
                monitor.debug("Closed connection to MQTT broker: " + brokerUrl);
            } catch (MqttException e) {
                monitor.warning("Error disconnecting from MQTT broker " + brokerUrl, e);
                throw e;
            }
        }
    }

    /**
     * Gets or creates an MQTT client for the given broker URL, reusing connections.
     */
    private org.eclipse.paho.client.mqttv3.MqttClient getOrCreateClient(String brokerUrl, String username, String password) throws MqttException {
        return clientCache.computeIfAbsent(brokerUrl, url -> {
            try {
                String clientId = "edc-opcua-mqtt-" + System.nanoTime();
                org.eclipse.paho.client.mqttv3.MqttClient client = new org.eclipse.paho.client.mqttv3.MqttClient(url, clientId);

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setAutomaticReconnect(true);

                // Configure authentication
                if (caCertPath != null && clientCertPath != null && clientKeyPath != null) {
                    // Certificate-based authentication
                    configureCertificateAuth(options);
                    monitor.info("Using certificate-based authentication");
                } else {
                    // Username/password authentication
                    if (username != null && !username.trim().isEmpty()) {
                        options.setUserName(username);
                    }
                    if (password != null && !password.trim().isEmpty()) {
                        options.setPassword(password.toCharArray());
                    }
                    monitor.info("Using username/password authentication");
                }

                client.connect(options);
                monitor.info("Connected to MQTT broker at " + url);
                return client;
            } catch (Exception e) {
                monitor.severe("Failed to connect to MQTT broker at " + url, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Configures SSL/TLS options for certificate-based authentication
     */
    private void configureCertificateAuth(MqttConnectOptions options) throws Exception {
        try {
            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // Configure trust manager (CA certificate)
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            // Load CA certificate
            try (InputStream caInputStream = new FileInputStream(caCertPath)) {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                X509Certificate caCert = (X509Certificate) certificateFactory.generateCertificate(caInputStream);
                trustStore.setCertificateEntry("ca-cert", caCert);
            }

            trustManagerFactory.init(trustStore);

            // Configure key manager (client certificate and private key)
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

            // Create keystore from PEM files
            KeyStore keyStore = createKeyStoreFromPemFiles(clientCertPath, clientKeyPath, clientKeyPassword);

            // Use empty password for keystore operations
            char[] keyStorePassword = "".toCharArray();
            keyManagerFactory.init(keyStore, keyStorePassword);

            // Initialize SSL context
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            // Set SSL properties
            options.setSSLProperties(createSslProperties());
            options.setSocketFactory(sslContext.getSocketFactory());

        } catch (Exception e) {
            monitor.severe("Failed to configure certificate authentication", e);
            throw new RuntimeException("Certificate configuration failed", e);
        }
    }


    /**
     * Creates SSL properties for the connection
     */
    private java.util.Properties createSslProperties() {
        java.util.Properties sslProps = new java.util.Properties();
        sslProps.setProperty("com.ibm.ssl.protocol", "TLSv1.2");
        sslProps.setProperty("com.ibm.ssl.contextProvider", "IBMJSSE2");
        sslProps.setProperty("com.ibm.ssl.keyStore", clientCertPath);
        sslProps.setProperty("com.ibm.ssl.keyStorePassword", clientKeyPassword != null ? clientKeyPassword : "");
        sslProps.setProperty("com.ibm.ssl.keyStoreType", "PKCS12");
        sslProps.setProperty("com.ibm.ssl.trustStore", caCertPath);
        sslProps.setProperty("com.ibm.ssl.trustStoreType", "JKS");
        return sslProps;
    }

    /**
     * Creates a KeyStore from PEM files
     * Note: This is a simplified implementation. For production use, consider using
     * a proper PEM parsing library like BouncyCastle.
     */
    private KeyStore createKeyStoreFromPemFiles(String certPath, String keyPath, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // Load certificate
        X509Certificate certificate;
        try (InputStream certInputStream = new FileInputStream(certPath)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) certificateFactory.generateCertificate(certInputStream);
        }

        // Load private key
        PrivateKey privateKey = loadPrivateKeyFromPem(keyPath, keyPassword);

        // Store in keystore
        keyStore.setKeyEntry("client-key", privateKey, "".toCharArray(), new Certificate[]{certificate});

        return keyStore;
    }

    private X509Certificate loadCertificateFromPem(String certPath) throws Exception {
        try {
            // Read the PEM file
            String pemContent = Files.readString(Paths.get(certPath), StandardCharsets.UTF_8);

            // Remove PEM headers/footers and whitespace
            String base64Content = pemContent
                    .replaceAll("-----BEGIN CERTIFICATE-----", "")
                    .replaceAll("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");

            // Decode Base64 content
            byte[] certBytes = Base64.getDecoder().decode(base64Content);

            // Create certificate from bytes
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        } catch (Exception e) {
            monitor.severe("Failed to load certificate from PEM file: " + certPath, e);
            throw new Exception("Failed to load certificate from PEM file: " + certPath, e);
        }
    }

    private PrivateKey loadPrivateKeyFromPem(String keyPath, String keyPassword) throws Exception {
        String keyContent = Files.readString(Paths.get(keyPath));

        // Remove PEM headers and footers
        keyContent = keyContent.replaceAll("-----BEGIN.*?-----", "")
                .replaceAll("-----END.*?-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);

        // Check if encrypted
        String originalContent = Files.readString(Paths.get(keyPath));
        boolean isEncrypted = originalContent.contains("ENCRYPTED");

        if (isEncrypted && keyPassword != null && !keyPassword.isEmpty()) {
            return loadEncryptedKey(keyBytes, keyPassword);
        } else {
            return loadUnencryptedKey(keyBytes);
        }
    }

    private PrivateKey loadEncryptedKey(byte[] keyBytes, String password) throws Exception {
        try {
            EncryptedPrivateKeyInfo encryptedInfo = new EncryptedPrivateKeyInfo(keyBytes);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(encryptedInfo.getAlgName());
            SecretKey secretKey = factory.generateSecret(new PBEKeySpec(password.toCharArray()));

            Cipher cipher = Cipher.getInstance(encryptedInfo.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedInfo.getAlgParameters());

            PKCS8EncodedKeySpec keySpec = encryptedInfo.getKeySpec(cipher);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            // Try EC if RSA fails
            try {
                EncryptedPrivateKeyInfo encryptedInfo = new EncryptedPrivateKeyInfo(keyBytes);
                SecretKeyFactory factory = SecretKeyFactory.getInstance(encryptedInfo.getAlgName());
                SecretKey secretKey = factory.generateSecret(new PBEKeySpec(password.toCharArray()));

                Cipher cipher = Cipher.getInstance(encryptedInfo.getAlgName());
                cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedInfo.getAlgParameters());

                PKCS8EncodedKeySpec keySpec = encryptedInfo.getKeySpec(cipher);
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                return keyFactory.generatePrivate(keySpec);
            } catch (Exception ec) {
                throw new RuntimeException("Failed to decrypt private key", e);
            }
        }
    }

    private PrivateKey loadUnencryptedKey(byte[] keyBytes) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Try RSA first
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
            // Try EC
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                return keyFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException ec) {
                throw new RuntimeException("Failed to load private key - unsupported format", e);
            }
        }
    }

    /**
     * Closes all cached MQTT connections. Should be called during shutdown.
     */
    public void close() {
        for (var entry : clientCache.entrySet()) {
            try {
                if (entry.getValue().isConnected()) {
                    entry.getValue().disconnect();
                }
                entry.getValue().close();
                monitor.info("Disconnected from MQTT broker: " + entry.getKey());
            } catch (MqttException e) {
                monitor.warning("Error disconnecting from MQTT broker " + entry.getKey(), e);
            }
        }
        clientCache.clear();
    }

    /**
     * Check if certificate-based authentication is configured
     */
    public boolean isCertificateAuthConfigured() {
        return caCertPath != null && clientCertPath != null && clientKeyPath != null;
    }

    /**
     * Check if username/password authentication is configured
     */
    public boolean isUsernamePasswordConfigured() {
        return username != null && password != null;
    }
}