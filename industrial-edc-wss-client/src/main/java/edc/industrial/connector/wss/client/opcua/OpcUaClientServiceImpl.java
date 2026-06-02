package edc.industrial.connector.wss.client.opcua;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * OPC-UA client implementation using Eclipse Milo SDK.
 *
 * Supports three authentication modes:
 *   - Anonymous (default constructor)
 *   - Username/Password (via factory method)
 *   - Certificate-based (via factory method)
 */
public class OpcUaClientServiceImpl implements OpcUaClientService {

    private static final Logger log = LoggerFactory.getLogger(OpcUaClientServiceImpl.class);

    private final IdentityProvider identityProvider;
    private final SecurityPolicy securityPolicy;
    private final MessageSecurityMode messageSecurityMode;

    /** Anonymous authentication. */
    public OpcUaClientServiceImpl() {
        this.identityProvider = new AnonymousProvider();
        this.securityPolicy = SecurityPolicy.None;
        this.messageSecurityMode = MessageSecurityMode.None;
    }

    private OpcUaClientServiceImpl(IdentityProvider identityProvider,
                                   SecurityPolicy securityPolicy,
                                   MessageSecurityMode messageSecurityMode) {
        this.identityProvider = identityProvider;
        this.securityPolicy = securityPolicy;
        this.messageSecurityMode = messageSecurityMode;
    }

    /** Factory method for username/password authentication. */
    public static OpcUaClientServiceImpl withCredentialsAuth(String username, String password) {
        try {
            var identityProvider = new UsernameProvider(username, password);
            return new OpcUaClientServiceImpl(identityProvider,
                    SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise username/password auth", e);
        }
    }

    /** Factory method for certificate-based authentication. */
    public static OpcUaClientServiceImpl withCertificateAuth(String clientCertPath, String clientKeyPath) {
        try {
            X509Certificate cert = loadCertificate(clientCertPath);
            PrivateKey key = loadPrivateKey(clientKeyPath);
            IdentityProvider identityProvider = new X509IdentityProvider(cert, key);
            return new OpcUaClientServiceImpl(identityProvider,
                    SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise certificate auth", e);
        }
    }

    @Override
    public Object readValue(String endpoint, String nodeId) throws Exception {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("OPC-UA endpoint must not be null/blank");
        }

        NodeId node = NodeId.parse(nodeId.trim());

        OpcUaClient client = OpcUaClient.create(
                endpoint.trim(),
                endpoints -> endpoints.stream()
                        .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
                        .filter(e -> e.getSecurityMode() == messageSecurityMode)
                        .findFirst()
                        .or(() -> endpoints.stream().findFirst()),
                configBuilder -> configBuilder
                        .setApplicationName(LocalizedText.english("OpcUa WSS Client"))
                        .setApplicationUri("urn:edc:industrial:connector:wss:client")
                        .setIdentityProvider(identityProvider)
                        .setRequestTimeout(UInteger.valueOf(15_000))
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
                log.warn("Error disconnecting OPC-UA client from {}", endpoint, e);
            }
        }
    }

    // ---- Certificate loading helpers ------------------------------------------------

    private static X509Certificate loadCertificate(String path) throws Exception {
        try {
            if (path.endsWith(".p12") || path.endsWith(".pfx")) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(path)) {
                    ks.load(fis, null);
                }
                String alias = ks.aliases().nextElement();
                return (X509Certificate) ks.getCertificate(alias);
            }
            return loadPemCertificate(path);
        } catch (Exception e) {
            throw new Exception("Failed to load certificate from " + path, e);
        }
    }

    private static X509Certificate loadPemCertificate(String path) throws Exception {
        String content = Files.readString(Paths.get(path));
        content = content.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(content);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
    }

    private static PrivateKey loadPrivateKey(String path) throws Exception {
        try {
            if (path.endsWith(".p12") || path.endsWith(".pfx")) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(path)) {
                    ks.load(fis, null);
                }
                String alias = ks.aliases().nextElement();
                return (PrivateKey) ks.getKey(alias, null);
            }
            return loadPemPrivateKey(path);
        } catch (Exception e) {
            throw new Exception("Failed to load private key from " + path, e);
        }
    }

    private static PrivateKey loadPemPrivateKey(String path) throws Exception {
        String content = Files.readString(Paths.get(path));
        if (content.contains("-----BEGIN PRIVATE KEY-----")) {
            // PKCS#8
            String stripped = content
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(stripped);
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } else if (content.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            return loadRsaPemPrivateKey(content);
        }
        throw new Exception("Unsupported private key format in " + path);
    }

    private static PrivateKey loadRsaPemPrivateKey(String keyContent) throws Exception {
        org.bouncycastle.openssl.PEMParser parser = new org.bouncycastle.openssl.PEMParser(new StringReader(keyContent));
        Object obj = parser.readObject();
        parser.close();
        org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();
        if (obj instanceof org.bouncycastle.openssl.PEMKeyPair pemKP) {
            return converter.getPrivateKey(pemKP.getPrivateKeyInfo());
        } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
            return converter.getPrivateKey(pki);
        }
        throw new Exception("Unexpected key object type: " + obj.getClass().getName());
    }
}

