
package org.eclipse.edc.common.spi.pki.custom;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.eclipse.edc.common.spi.pki.PkiCertificateService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import javax.security.auth.x500.X500Principal;

public class PkiCertificateServiceImpl implements PkiCertificateService {

    private final PkiConfig pkiConfig;
    private final Monitor monitor;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PkiCertificateServiceImpl(PkiConfig pkiConfig, Monitor monitor) {
        this.pkiConfig = pkiConfig;
        this.monitor = monitor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Result<String> requestCertificate(String csrPem, String commonName, int validityDays) {
        if (!pkiConfig.isValid()) {
            return Result.failure("PKI configuration is invalid or incomplete");
        }

        try {
            // Create request body with the actual CSR
            CertificateRequest request = new CertificateRequest(csrPem, validityDays, commonName);
            String requestBody = objectMapper.writeValueAsString(request);

            // Build HTTP request using the PKI config endpoint and API key
            String endpoint = pkiConfig.getCertificateRequestUrl();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("X-Api-Key", pkiConfig.getApiKey())  // Use API key from config
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            monitor.debug("Requesting certificate from PKI endpoint: " + endpoint);
            monitor.debug("Common Name: " + commonName + ", Validity Days: " + validityDays);
            monitor.debug("CSR length: " + csrPem.length() + " characters");

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Parse response
                CertificateResponse certificateResponse = objectMapper.readValue(response.body(), CertificateResponse.class);

                monitor.info("Certificate successfully obtained from PKI. Serial: " + certificateResponse.serialNumber);
                monitor.info("Certificate valid from: " + certificateResponse.issuedAt + " to: " + certificateResponse.expiresAt);
                return Result.success(certificateResponse.certificatePem);

            } else {
                monitor.warning("PKI certificate request failed with status: " + response.statusCode());
                monitor.warning("Response body: " + response.body());
                return Result.failure("PKI request failed with status " + response.statusCode() + ": " + response.body());
            }

        } catch (IOException e) {
            monitor.severe("IO error during PKI certificate request", e);
            return Result.failure("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            monitor.severe("PKI certificate request was interrupted", e);
            Thread.currentThread().interrupt();
            return Result.failure("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            monitor.severe("Unexpected error during PKI certificate request", e);
            return Result.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public Result<String> getCertificateChain() {
        if (!pkiConfig.isValid()) {
            return Result.failure("PKI configuration is invalid or incomplete");
        }

        try {
            // Build the CA chain endpoint URL
            String endpoint = pkiConfig.getEndpoint() + "/api/Pki/ca-chain";

            // Build HTTP request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("X-Api-Key", pkiConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();

            monitor.debug("Requesting CA chain from PKI endpoint: " + endpoint);

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Parse response
                CaChainResponse caChainResponse = objectMapper.readValue(response.body(), CaChainResponse.class);

                monitor.info("CA chain successfully obtained from PKI");

                // Combine root and intermediate certificates into a single chain
                StringBuilder chainBuilder = new StringBuilder();

                // Add root certificate
                if (caChainResponse.rootCertificate != null && !caChainResponse.rootCertificate.trim().isEmpty()) {
                    String rootCert = caChainResponse.rootCertificate.trim();
                    chainBuilder.append(rootCert);
                    // Always ensure there's a newline after the certificate
                    if (!rootCert.endsWith("\n")) {
                        chainBuilder.append("\n");
                    }
                }

                // Add intermediate certificate
                if (caChainResponse.intermediateCertificate != null && !caChainResponse.intermediateCertificate.trim().isEmpty()) {
                    String intermediateCert = caChainResponse.intermediateCertificate.trim();
                    chainBuilder.append(intermediateCert);
                    // Always ensure there's a newline after the certificate
                    if (!intermediateCert.endsWith("\n")) {
                        chainBuilder.append("\n");
                    }
                }

                return Result.success(chainBuilder.toString());

            } else {
                monitor.warning("PKI CA chain request failed with status: " + response.statusCode());
                monitor.warning("Response body: " + response.body());
                return Result.failure("PKI CA chain request failed with status " + response.statusCode() + ": " + response.body());
            }

        } catch (IOException e) {
            monitor.severe("IO error during PKI CA chain request", e);
            return Result.failure("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            monitor.severe("PKI CA chain request was interrupted", e);
            Thread.currentThread().interrupt();
            return Result.failure("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            monitor.severe("Unexpected error during PKI CA chain request", e);
            return Result.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public Result<String> getCommonName(String certificatePem) {
        try {
            return Result.success(extractCommonNameFromCsr(certificatePem));
        } catch (Exception e) {
            return Result.failure("Failed to extract CN from certificate: " + e.getMessage());
        }
    }

    public static String extractCommonNameFromCsr(String csrPem) throws Exception {
        // Remove PEM headers and decode
        String csrContent = csrPem.replaceAll("-----BEGIN.*?-----", "")
                .replaceAll("-----END.*?-----", "")
                .replaceAll("\\s", "");

        byte[] csrBytes = Base64.getDecoder().decode(csrContent);

        // Parse CSR using BouncyCastle (if available)
        try {
            PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrBytes);
            X500Principal subject = new X500Principal(csr.getSubject().getEncoded());
            return extractCommonNameFromX500Principal(subject);
        } catch (Exception e) {
            // Alternative approach using PEM parser
            try (StringReader stringReader = new StringReader(csrPem); PEMParser pemParser = new PEMParser(stringReader)) {

                PKCS10CertificationRequest csr = (PKCS10CertificationRequest) pemParser.readObject();
                X500Principal subject = new X500Principal(csr.getSubject().getEncoded());
                return extractCommonNameFromX500Principal(subject);
            }
        }
    }

    private static String extractCommonNameFromX500Principal(X500Principal subject) {
        String subjectDistinguishedName = subject.getName();

        // Parse the DN string to find CN
        String[] dnComponents = subjectDistinguishedName.split(",");
        for (String component : dnComponents) {
            String trimmed = component.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }

        return null; // CN not found
    }

    // Request DTO
    private static class CertificateRequest {
        @JsonProperty("csrPem")
        public final String csrPem;

        @JsonProperty("validityDays")
        public final int validityDays;

        @JsonProperty("commonName")
        public final String commonName;

        CertificateRequest(String csrPem, int validityDays, String commonName) {
            this.csrPem = csrPem;
            this.validityDays = validityDays;
            this.commonName = commonName;
        }
    }

    // Response DTO
    private static class CertificateResponse {
        @JsonProperty("certificatePem")
        public String certificatePem;

        @JsonProperty("serialNumber")
        public String serialNumber;

        @JsonProperty("issuedAt")
        public String issuedAt;

        @JsonProperty("expiresAt")
        public String expiresAt;
    }

    // Response DTO for CA Chain
    private static class CaChainResponse {
        @JsonProperty("rootCertificate")
        public String rootCertificate;

        @JsonProperty("intermediateCertificate")
        public String intermediateCertificate;
    }
}