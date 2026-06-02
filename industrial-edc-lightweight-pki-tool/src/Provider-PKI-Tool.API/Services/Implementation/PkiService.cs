using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;

namespace Provider_PKI_Tool.API.Services.Implementation;

using System.Security.Cryptography.X509Certificates;
using Org.BouncyCastle.Asn1.X509;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Math;
using Org.BouncyCastle.OpenSsl;
using Org.BouncyCastle.Pkcs;
using Org.BouncyCastle.X509;
using X509Certificate = Org.BouncyCastle.X509.X509Certificate;
using Org.BouncyCastle.Crypto.Operators;
using Provider_PKI_Tool.API.Models;

public class PkiService : IPkiService
{
    private readonly ICertificateLoaderService _certificateLoader;
    private readonly ILogger<PkiService> _logger;
    private X509Certificate _intermediateCert;
    private AsymmetricKeyParameter _intermediatePrivateKey;
    private X509Certificate _rootCert;

    public PkiService(ICertificateLoaderService certificateLoader, ILogger<PkiService> logger)
    {
        _certificateLoader = certificateLoader;
        _logger = logger;
        Initialize();
    }

    private void Initialize()
    {
        try
        {
            var intermediateNetCert = _certificateLoader.LoadIntermediateCertificate();
            var rootNetCert = _certificateLoader.LoadRootCertificate();

            _intermediateCert = ConvertNetCertToBouncyCastle(intermediateNetCert);
            _intermediatePrivateKey = GetPrivateKeyFromNetCert(intermediateNetCert);
            _rootCert = ConvertNetCertToBouncyCastle(rootNetCert);

            _logger.LogInformation("PKI Service initialized successfully");
        }
        catch (Exception ex)
        {
            _logger.LogError($"Failed to initialize PKI Service: {ex.Message}");
            throw;
        }
    }

    public async Task<CertificateResponseDto> SignCsrAsync(string csrPem, int validityDays)
    {
        try
        {
            _logger.LogInformation("Starting CSR signing process");

            // Parse CSR
            var csr = ParseCsr(csrPem);
            
            // Validate CSR
            if (!csr.Verify())
            {
                throw new InvalidOperationException("CSR signature verification failed");
            }

            // Generate certificate
            var certificate = GenerateCertificate(csr, validityDays);
            var certPem = ConvertCertToPem(certificate);

            var response = new CertificateResponseDto
            {
                CertificatePem = certPem,
                SerialNumber = certificate.SerialNumber.ToString(),
                IssuedAt = certificate.NotBefore,
                ExpiresAt = certificate.NotAfter
            };

            _logger.LogInformation($"Certificate signed successfully. Serial: {certificate.SerialNumber}");
            return await Task.FromResult(response);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error signing CSR: {ex.Message}");
            throw;
        }
    }

    public async Task<CertificateResponseDto> RenewCertificateAsync(string certificatePem, int validityDays)
    {
        try
        {
            _logger.LogInformation("Starting certificate renewal process");

            // Parse existing certificate
            var existingCert = ParseCertificate(certificatePem);
            
            // Extract CSR-like info from existing certificate
            var subjectDn = existingCert.SubjectDN;
            
            // Generate new certificate with same subject
            var newCert = GenerateCertificateFromSubject(subjectDn, validityDays);
            var certPem = ConvertCertToPem(newCert);

            var response = new CertificateResponseDto
            {
                CertificatePem = certPem,
                SerialNumber = newCert.SerialNumber.ToString(),
                IssuedAt = newCert.NotBefore,
                ExpiresAt = newCert.NotAfter
            };

            _logger.LogInformation($"Certificate renewed successfully. Serial: {newCert.SerialNumber}");
            return await Task.FromResult(response);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error renewing certificate: {ex.Message}");
            throw;
        }
    }

    public async Task RevokeCertificateAsync(string certificatePem, string? reason)
    {
        try
        {
            var cert = ParseCertificate(certificatePem);
            var serial = cert.SerialNumber.ToString();

            _logger.LogWarning($"Certificate revoked. Serial: {serial}, Reason: {reason}");
            
            // TODO: Implement CRL/OCSP revocation storage
            await Task.CompletedTask;
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error revoking certificate: {ex.Message}");
            throw;
        }
    }

    public async Task<CaChainResponseDto> GetCaChainAsync()
    {
        try
        {
            var rootPem = ConvertCertToPem(_rootCert);
            var intermediatePem = ConvertCertToPem(_intermediateCert);

            return await Task.FromResult(new CaChainResponseDto
            {
                RootCertificate = rootPem,
                IntermediateCertificate = intermediatePem
            });
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error retrieving CA chain: {ex.Message}");
            throw;
        }
    }

    public async Task<CsrValidationResponseDto> ValidateCsrAsync(string csrPem)
    {
        try
        {
            var csr = ParseCsr(csrPem);
            var isValid = csr.Verify();

            var response = new CsrValidationResponseDto
            {
                IsValid = isValid,
                Subject = csr.GetCertificationRequestInfo().Subject.ToString()
            };

            if (isValid)
            {
                var sanExtension = GetSanExtension(csr);
                if (sanExtension != null)
                {
                    response.SubjectAlternativeNames = ExtractSanValues(sanExtension);
                }
            }
            else
            {
                response.ErrorMessage = "CSR signature verification failed";
            }

            _logger.LogInformation($"CSR validation completed. Valid: {isValid}");
            return await Task.FromResult(response);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error validating CSR: {ex.Message}");
            return new CsrValidationResponseDto
            {
                IsValid = false,
                ErrorMessage = ex.Message
            };
        }
    }

    // Helper methods
    private Pkcs10CertificationRequest ParseCsr(string csrPem)
    {
        using (var reader = new StringReader(csrPem))
        {
            var pemReader = new PemReader(reader);
            return (Pkcs10CertificationRequest)pemReader.ReadObject();
        }
    }

    private X509Certificate ParseCertificate(string certPem)
    {
        using (var reader = new StringReader(certPem))
        {
            var pemReader = new PemReader(reader);
            return (X509Certificate)pemReader.ReadObject();
        }
    }

    private X509Certificate GenerateCertificate(Pkcs10CertificationRequest csr, int validityDays)
    {
        var certInfo = csr.GetCertificationRequestInfo();
        var subject = certInfo.Subject;
        var publicKey = csr.GetPublicKey();

        var serialNumber = BigInteger.ValueOf(DateTime.UtcNow.Ticks);
        var notBefore = DateTime.UtcNow;
        var notAfter = notBefore.AddDays(validityDays);

        var generator = new X509V3CertificateGenerator();
        generator.SetSerialNumber(serialNumber);
        generator.SetSubjectDN(subject);
        generator.SetIssuerDN(_intermediateCert.SubjectDN);
        generator.SetNotBefore(notBefore);
        generator.SetNotAfter(notAfter);
        generator.SetPublicKey(publicKey);

        // Add extensions
        AddCertificateExtensions(generator, csr);

        var cert = generator.Generate(new Asn1SignatureFactory("SHA256WithRSA", _intermediatePrivateKey));
        return cert;
    }

    private X509Certificate GenerateCertificateFromSubject(X509Name subject, int validityDays)
    {
        var serialNumber = BigInteger.ValueOf(DateTime.UtcNow.Ticks);
        var notBefore = DateTime.UtcNow;
        var notAfter = notBefore.AddDays(validityDays);

        // TODO: Generate new key pair for renewal
        // For now, this is a simplified implementation
        
        var generator = new X509V3CertificateGenerator();
        generator.SetSerialNumber(serialNumber);
        generator.SetSubjectDN(subject);
        generator.SetIssuerDN(_intermediateCert.SubjectDN);
        generator.SetNotBefore(notBefore);
        generator.SetNotAfter(notAfter);

        var cert = generator.Generate(new Asn1SignatureFactory("SHA256WithRSA", _intermediatePrivateKey));
        return cert;
    }

    private void AddCertificateExtensions(X509V3CertificateGenerator generator, Pkcs10CertificationRequest csr)
    {
        var extensions = csr.GetRequestedExtensions();
        
        if (extensions != null)
        {
            foreach (var oid in extensions.GetExtensionOids())
            {
                var ext = extensions.GetExtension(oid);
                generator.AddExtension(oid, ext.IsCritical, ext.GetParsedValue());
            }
        }

        // Add basic constraints if not present
        if (extensions?.GetExtension(X509Extensions.BasicConstraints) == null)
        {
            generator.AddExtension(X509Extensions.BasicConstraints, true, 
                new BasicConstraints(false));
        }

        // Add key usage if not present
        if (extensions?.GetExtension(X509Extensions.KeyUsage) == null)
        {
            generator.AddExtension(X509Extensions.KeyUsage, true,
                new KeyUsage(KeyUsage.DigitalSignature | KeyUsage.KeyEncipherment));
        }
    }

    private Org.BouncyCastle.Asn1.X509.X509Extension? GetSanExtension(Pkcs10CertificationRequest csr)
    {
        var extensions = csr.GetRequestedExtensions();
        return extensions?.GetExtension(X509Extensions.SubjectAlternativeName);
    }

    private string[] ExtractSanValues(Org.BouncyCastle.Asn1.X509.X509Extension extension)
    {
        try
        {
            var generalNames = GeneralNames.GetInstance(extension.GetParsedValue());
            return generalNames.GetNames()
                .Select(gn => gn.Name.ToString())
                .ToArray();
        }
        catch
        {
            return Array.Empty<string>();
        }
    }

    private X509Certificate ConvertNetCertToBouncyCastle(System.Security.Cryptography.X509Certificates.X509Certificate2 netCert)
    {
        var parser = new X509CertificateParser();
        return parser.ReadCertificate(netCert.RawData);
    }

    private AsymmetricKeyParameter GetPrivateKeyFromNetCert(System.Security.Cryptography.X509Certificates.X509Certificate2 netCert)
    {
        var rsa = netCert.GetRSAPrivateKey();
        var rsaParams = rsa.ExportParameters(true);
        
        var modulus = new BigInteger(1, rsaParams.Modulus);
        var publicExp = new BigInteger(1, rsaParams.Exponent);
        var privateExp = new BigInteger(1, rsaParams.D);
        var prime1 = new BigInteger(1, rsaParams.P);
        var prime2 = new BigInteger(1, rsaParams.Q);
        var exponent1 = new BigInteger(1, rsaParams.DP);
        var exponent2 = new BigInteger(1, rsaParams.DQ);
        var crtCoeff = new BigInteger(1, rsaParams.InverseQ);

        var keySpec = new Org.BouncyCastle.Crypto.Parameters.RsaPrivateCrtKeyParameters(
            modulus, publicExp, privateExp, prime1, prime2, exponent1, exponent2, crtCoeff);

        return keySpec;
    }

    private string ConvertCertToPem(X509Certificate cert)
    {
        using (var writer = new StringWriter())
        {
            var pemWriter = new PemWriter(writer);
            pemWriter.WriteObject(cert);
            pemWriter.Writer.Flush();
            return writer.ToString();
        }
    }
}