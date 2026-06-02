package edc.industrial.connector.wss.client.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Eclipse Paho v3 based MQTT publisher.
 *
 * Supports:
 *   - Anonymous connection (broker URL only)
 *   - Username/password authentication
 *   - TLS certificate-based authentication
 *
 * For TLS, each cert argument can be either:
 *   (a) a file-system path to a PEM/PKCS12 file, or
 *   (b) inline PEM content (the raw "-----BEGIN CERTIFICATE-----..." string).
 * The private key for the client identity must always be a file-system path
 * (private keys are never transmitted over the wire).
 */
public class PahoMqttPublisherServiceImpl implements MqttPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PahoMqttPublisherServiceImpl.class);

    private final MqttClient client;

    /**
     * Creates a publisher with username/password (or anonymous if both are null).
     */
    public PahoMqttPublisherServiceImpl(String brokerUrl, String username, String password) throws Exception {
        String clientId = "industrial-connector-wss-client-" + UUID.randomUUID().toString().substring(0, 8);
        this.client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        if (username != null && !username.isBlank()) {
            opts.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            opts.setPassword(password.toCharArray());
        }

        log.info("Connecting to MQTT broker {} (user: {})", brokerUrl, username != null ? "***" : "anonymous");
        client.connect(opts);
        log.info("Connected to MQTT broker {}", brokerUrl);
    }

    /**
     * Creates a publisher with TLS certificate-based authentication.
     *
     * @param brokerUrl        broker URL (ssl://...)
     * @param caCertPemOrPath  CA chain – inline PEM string OR file path
     * @param clientCertPath   client certificate – file path (PEM or PKCS12)
     * @param clientKeyPath    client private key – file path (PEM PKCS8/RSA or PKCS12)
     */
    public PahoMqttPublisherServiceImpl(String brokerUrl,
                                        String caCertPemOrPath,
                                        String clientCertPath,
                                        String clientKeyPath) throws Exception {
        String clientId = "industrial-connector-wss-client-" + UUID.randomUUID().toString().substring(0, 8);
        this.client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setSocketFactory(buildSslSocketFactory(caCertPemOrPath, clientCertPath, clientKeyPath));

        log.info("Connecting to MQTT broker {} with TLS certificate (clientCert={})", brokerUrl, clientCertPath);
        client.connect(opts);
        log.info("Connected to MQTT broker {} (TLS)", brokerUrl);
    }

    // ---- MqttPublisherService impl -------------------------------------------------

    @Override
    public void publish(String topic, byte[] payload, int qos) throws Exception {
        if (!client.isConnected()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        MqttMessage msg = new MqttMessage(payload);
        msg.setQos(qos);
        msg.setRetained(false);
        client.publish(topic, msg);
        log.debug("Published {} bytes to topic '{}'", payload.length, topic);
    }

    @Override
    public void close() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            log.info("MQTT client closed");
        } catch (Exception e) {
            log.warn("Error closing MQTT client", e);
        }
    }

    // ---- TLS helpers ---------------------------------------------------------------

    /**
     * Builds an SSLSocketFactory.
     *
     * @param caCertPemOrPath inline PEM string or file path for the CA / CA-chain
     * @param clientCertPath  file path for the client certificate (PEM or PKCS12); null = no mutual TLS
     * @param clientKeyPath   file path for the client private key; null = no mutual TLS
     */
    private static javax.net.ssl.SSLSocketFactory buildSslSocketFactory(
            String caCertPemOrPath,
            String clientCertPath,
            String clientKeyPath) throws Exception {

        // ---- Trust store (CA chain) ----
        // Supports a single cert or a chain of multiple PEM certs concatenated together.
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        List<X509Certificate> caCerts = loadCertificates(caCertPemOrPath);
        for (int i = 0; i < caCerts.size(); i++) {
            trustStore.setCertificateEntry("ca-" + i, caCerts.get(i));
        }
        log.debug("Trust store loaded with {} CA certificate(s)", caCerts.size());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // ---- Key store (client identity) ----
        // Only built when both client cert and key paths are provided.
        KeyManagerFactory kmf = null;
        if (clientCertPath != null && !clientCertPath.isBlank()
                && clientKeyPath != null && !clientKeyPath.isBlank()) {

            KeyStore keyStore;
            if (clientCertPath.endsWith(".p12") || clientCertPath.endsWith(".pfx")) {
                java.nio.file.Path resolvedP12 = Paths.get(clientCertPath).toAbsolutePath();
                if (!java.nio.file.Files.exists(resolvedP12)) {
                    throw new java.io.FileNotFoundException(
                            "PKCS12 keystore file not found: " + resolvedP12 +
                            "\nCheck MQTT_CLIENT_CERT_PATH in your env file and ensure the file exists.");
                }
                keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(resolvedP12.toFile())) {
                    keyStore.load(fis, null);
                }
            } else {
                keyStore = buildKeyStoreFromPemFiles(clientCertPath, clientKeyPath);
            }

            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);
            log.debug("Key store loaded from cert={}, key={}", clientCertPath, clientKeyPath);
        } else {
            log.debug("No client cert/key provided – server-side TLS only (no mutual TLS)");
        }

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(
                kmf != null ? kmf.getKeyManagers() : null,
                tmf.getTrustManagers(),
                null);
        return sslContext.getSocketFactory();
    }

    /**
     * Loads one or more X.509 certificates from either inline PEM content or a file path.
     * Handles concatenated PEM chains (multiple "-----BEGIN CERTIFICATE-----" blocks).
     */
    static List<X509Certificate> loadCertificates(String pemOrPath) throws Exception {
        String pemContent = isInlinePem(pemOrPath)
                ? pemOrPath
                : Files.readString(Paths.get(pemOrPath));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = new ArrayList<>();

        // Split on certificate boundaries to handle chains
        String[] blocks = pemContent.split("(?=-----BEGIN CERTIFICATE-----)");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            // Normalise escaped newlines that may arrive in JSON strings
            block = block.replace("\\n", "\n");
            byte[] bytes = block.getBytes(StandardCharsets.UTF_8);
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                certs.add((X509Certificate) cf.generateCertificate(is));
            }
        }

        if (certs.isEmpty()) {
            throw new Exception("No X.509 certificates found in: " +
                    (isInlinePem(pemOrPath) ? "<inline PEM>" : pemOrPath));
        }
        return certs;
    }

    /**
     * Builds a PKCS12 KeyStore from PEM certificate and private key files.
     */
    private static KeyStore buildKeyStoreFromPemFiles(String certPath, String keyPath) throws Exception {
        java.nio.file.Path resolvedCertPath = Paths.get(certPath).toAbsolutePath();
        java.nio.file.Path resolvedKeyPath  = Paths.get(keyPath).toAbsolutePath();

        if (!java.nio.file.Files.exists(resolvedCertPath)) {
            throw new java.io.FileNotFoundException(
                    "Client certificate file not found: " + resolvedCertPath +
                    "\nCheck MQTT_CLIENT_CERT_PATH in your env file and ensure the file exists.");
        }
        if (!java.nio.file.Files.exists(resolvedKeyPath)) {
            throw new java.io.FileNotFoundException(
                    "Client private key file not found: " + resolvedKeyPath +
                    "\nCheck MQTT_CLIENT_KEY_PATH in your env file and ensure the file exists.");
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (FileInputStream fis = new FileInputStream(resolvedCertPath.toFile())) {
            cert = (X509Certificate) cf.generateCertificate(fis);
        }

        java.security.PrivateKey privateKey = loadPemPrivateKeyFromFile(resolvedKeyPath.toString());

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("client", privateKey, new char[0], new Certificate[]{cert});
        return ks;
    }

    /**
     * Loads a private key from a PEM file (PKCS8 or RSA traditional format).
     */
    private static java.security.PrivateKey loadPemPrivateKeyFromFile(String path) throws Exception {
        String content = Files.readString(Paths.get(path));
        return parsePemPrivateKey(content, path);
    }

    private static java.security.PrivateKey parsePemPrivateKey(String content, String source) throws Exception {
        if (content.contains("-----BEGIN PRIVATE KEY-----")) {
            // PKCS#8 unencrypted
            String stripped = content
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(stripped);
            return java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(decoded));
        }
        // RSA traditional / SEC1 format – use BouncyCastle
        try (var reader = new java.io.StringReader(content)) {
            var parser = new org.bouncycastle.openssl.PEMParser(reader);
            Object obj = parser.readObject();
            parser.close();
            var converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                return converter.getPrivateKey(kp.getPrivateKeyInfo());
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                return converter.getPrivateKey(pki);
            }
        }
        throw new Exception("Unsupported private key format in " + source);
    }

    /** Returns true when the value looks like raw PEM content rather than a file path. */
    private static boolean isInlinePem(String value) {
        return value != null && (value.contains("-----BEGIN ") || value.contains("\\n-----BEGIN "));
    }
}

