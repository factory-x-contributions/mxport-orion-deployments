package org.eclipse.edc.common.spi.pki;

import org.eclipse.edc.spi.result.Result;

/**
 * Service for requesting certificates from a PKI endpoint.
 */
public interface PkiCertificateService {

    /**
     * Requests a certificate from the PKI endpoint.
     *
     * @param csrPem the Certificate Signing Request in PEM format
     * @param commonName the common name for the certificate
     * @param validityDays the validity period in days
     * @return Result containing the certificate PEM string or failure details
     */
    Result<String> requestCertificate(String csrPem, String commonName, int validityDays);

    /**
     * Retrieves the certificate chain from the PKI endpoint.
     *
     * @return Result containing the certificate chain in PEM format or failure details
     */
    Result<String> getCertificateChain();

    /**
     * Retrieves the common name from a certificate.
     *
     * @param certificatePem the Certificate in PEM format
     * @return String containing the common name or failure details
     */
    Result<String> getCommonName(String certificatePem);
}