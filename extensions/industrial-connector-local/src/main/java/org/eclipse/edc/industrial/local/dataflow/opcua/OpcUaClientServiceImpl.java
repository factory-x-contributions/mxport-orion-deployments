package org.eclipse.edc.industrial.local.dataflow.opcua;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * OPC UA client implementation for reading values from OPC UA servers.
 * Uses Eclipse Milo SDK and is completely independent of other OPC UA extensions.
 *
 * Supports three authentication modes:
 * - Anonymous (default constructor)
 * - Username/Password (constructor with username/password)
 * - Certificate-based (static factory method)
 */
public class OpcUaClientServiceImpl implements OpcUaClientService {

    private final Monitor monitor;
    private final IdentityProvider identityProvider;
    private final SecurityPolicy securityPolicy;
    private final MessageSecurityMode messageSecurityMode;

    /**
     * Constructor for anonymous authentication.
     */
    public OpcUaClientServiceImpl(Monitor monitor) {
        this.monitor = monitor;
        this.identityProvider = new AnonymousProvider();
        this.securityPolicy = SecurityPolicy.None;
        this.messageSecurityMode = MessageSecurityMode.None;
    }

    /**
     * Private constructor used by factory method for certificate-based authentication.
     */
    private OpcUaClientServiceImpl(Monitor monitor, IdentityProvider identityProvider,
                                   SecurityPolicy securityPolicy, MessageSecurityMode messageSecurityMode) {
        this.monitor = monitor;
        this.identityProvider = identityProvider;
        this.securityPolicy = securityPolicy;
        this.messageSecurityMode = messageSecurityMode;
    }

    /**
     * Factory method for credentials-based authentication.
     */
    public static OpcUaClientServiceImpl withCredentialsAuth(Monitor monitor, String username, String password) {
        try {
            var identityProvider = new UsernameProvider(username, password);
            var securityPolicy = SecurityPolicy.Basic256Sha256;
            var messageSecurityMode = MessageSecurityMode.SignAndEncrypt;

            return new OpcUaClientServiceImpl(monitor, identityProvider,
                    securityPolicy, messageSecurityMode);
        } catch (Exception e) {
            monitor.severe("Failed to initialize certificate-based authentication", e);
            throw new RuntimeException("Failed to load certificate or private key", e);
        }
    }

    /**
     * Factory method for certificate-based authentication.
     */
    public static OpcUaClientServiceImpl withCertificateAuth(Monitor monitor, String clientCertificatePath, String clientPrivateKeyPath) {
        try {
            X509Certificate certificate = loadCertificate(clientCertificatePath);
            PrivateKey privateKey = loadPrivateKey(clientPrivateKeyPath);
            IdentityProvider identityProvider = new X509IdentityProvider(certificate, privateKey);

            return new OpcUaClientServiceImpl(monitor, identityProvider,
                    SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);
        } catch (Exception e) {
            monitor.severe("Failed to initialize certificate-based authentication", e);
            throw new RuntimeException("Failed to load certificate or private key", e);
        }
    }

    @Override
    public Object readValue(String endpoint, String nodeId) throws Exception {
        var endpointUrl = endpoint == null ? null : endpoint.trim();
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            throw new IllegalArgumentException("OPC UA endpoint must not be null/blank");
        }

        var node = NodeId.parse(nodeId.trim());

        var client = OpcUaClient.create(
                endpointUrl,
                endpoints -> endpoints.stream()
                        .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
                        .filter(e -> e.getSecurityMode() == messageSecurityMode)
                        .findFirst()
                        .or(() -> endpoints.stream().findFirst()),
                configBuilder -> configBuilder
                        .setApplicationName(LocalizedText.english("EDC OPC UA MQTT Client"))
                        .setApplicationUri("urn:edc:opcuamqtt:client")
                        .setIdentityProvider(identityProvider)
                        .setRequestTimeout(UInteger.valueOf(15000))
                        .build()
        );

        try {
            client.connect().get();
            var dataValue = client.readValue(0, TimestampsToReturn.Both, node).get();
            return dataValue.getValue().getValue();
        } finally {
            try {
                client.disconnect().get();
            } catch (Exception e) {
                monitor.warning("Error disconnecting OPC UA client from " + endpoint, e);
            }
        }
    }

    private static X509Certificate loadCertificate(String certificatePath) throws Exception {
        try {
            if (certificatePath.endsWith(".p12") || certificatePath.endsWith(".pfx")) {
                // Load from PKCS#12 keystore
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(certificatePath)) {
                    keyStore.load(fis, null); // Assuming no password for simplicity
                }
                String alias = keyStore.aliases().nextElement();
                return (X509Certificate) keyStore.getCertificate(alias);
            } else {
                // Load PEM certificate using standard Java
                return loadPemCertificate(certificatePath);
            }
        } catch (Exception e) {
            throw new Exception("Failed to load certificate from " + certificatePath, e);
        }
    }

    private static X509Certificate loadPemCertificate(String certificatePath) throws Exception {
        try {
            byte[] certBytes = Files.readAllBytes(Paths.get(certificatePath));
            String certContent = new String(certBytes);

            // Remove PEM headers and footers, and newlines
            certContent = certContent
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = java.util.Base64.getDecoder().decode(certContent);

            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(decoded));
        } catch (Exception e) {
            throw new Exception("Failed to parse PEM certificate", e);
        }
    }

    private static PrivateKey loadPrivateKey(String privateKeyPath) throws Exception {
        try {
            if (privateKeyPath.endsWith(".p12") || privateKeyPath.endsWith(".pfx")) {
                // Load from PKCS#12 keystore
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(privateKeyPath)) {
                    keyStore.load(fis, null); // Assuming no password for simplicity
                }
                String alias = keyStore.aliases().nextElement();
                return (PrivateKey) keyStore.getKey(alias, null);
            } else {
                // Load PEM private key
                return loadPemPrivateKey(privateKeyPath);
            }
        } catch (Exception e) {
            throw new Exception("Failed to load private key from " + privateKeyPath, e);
        }
    }

    private static PrivateKey loadPemPrivateKey(String privateKeyPath) throws Exception {
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
            String keyContent = new String(keyBytes);

            // Handle different PEM key formats
            if (keyContent.contains("-----BEGIN PRIVATE KEY-----")) {
                // PKCS#8 format
                keyContent = keyContent
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");

                byte[] decoded = java.util.Base64.getDecoder().decode(keyContent);
                java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);
            } else if (keyContent.contains("-----BEGIN RSA PRIVATE KEY-----")) {
                // Traditional RSA format - requires BouncyCastle for parsing
                return loadRsaPemPrivateKey(keyContent);
            } else {
                throw new Exception("Unsupported private key format");
            }
        } catch (Exception e) {
            throw new Exception("Failed to parse PEM private key", e);
        }
    }

    private static PrivateKey loadRsaPemPrivateKey(String keyContent) throws Exception {
        try {
            // Use BouncyCastle to parse traditional RSA private key format
            org.bouncycastle.openssl.PEMParser pemParser = new org.bouncycastle.openssl.PEMParser(
                    new java.io.StringReader(keyContent)
            );

            Object keyPair = pemParser.readObject();
            pemParser.close();

            if (keyPair instanceof org.bouncycastle.openssl.PEMKeyPair) {
                org.bouncycastle.openssl.PEMKeyPair pemKeyPair = (org.bouncycastle.openssl.PEMKeyPair) keyPair;
                org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();
                return converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
            } else if (keyPair instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();
                return converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) keyPair);
            } else {
                throw new Exception("Unexpected key type: " + keyPair.getClass().getName());
            }
        } catch (Exception e) {
            throw new Exception("Failed to parse RSA PEM private key using BouncyCastle", e);
        }
    }
}