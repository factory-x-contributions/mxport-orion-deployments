using System;
using System.IO;
using Microsoft.Extensions.Logging;

namespace Consumer_PKI_Tool.API.Services.Implementation;

using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

public class CertificateLoaderService : ICertificateLoaderService
{
    private readonly IConfigurationService _configurationService;
    private readonly ILogger<CertificateLoaderService> _logger;

    public CertificateLoaderService(IConfigurationService configurationService, ILogger<CertificateLoaderService> logger)
    {
        _configurationService = configurationService;
        _logger = logger;
    }

    public X509Certificate2 LoadClientCertificate(string? password = null)
    {
        try
        {
            var certPath = ResolvePath(_configurationService.ClientCA_CertificatePath);
            var keyPath = ResolvePath(_configurationService.ClientCA_PrivateKeyPath);
            var effectivePassword = string.IsNullOrWhiteSpace(password)
                ? null
                : password.Trim();

            EnsureDirectoryExists(certPath);
            EnsureDirectoryExists(keyPath);

            if (!File.Exists(certPath) || !File.Exists(keyPath))
            {
                _logger.LogWarning("Client certificate or private key not found. Creating a new self-signed client certificate.");
                CreateClientCertificate(certPath, keyPath, effectivePassword);
            }

            _logger.LogInformation("Loading client certificate from {CertificatePath}", certPath);

            X509Certificate2 certificate;

            if (string.IsNullOrWhiteSpace(effectivePassword))
            {
                certificate = X509Certificate2.CreateFromPemFile(certPath, keyPath);

                return new X509Certificate2(
                    certificate.Export(X509ContentType.Pkcs12),
                    (string?)null,
                    X509KeyStorageFlags.PersistKeySet | X509KeyStorageFlags.Exportable);
            }

            certificate = X509Certificate2.CreateFromEncryptedPemFile(certPath, effectivePassword, keyPath);

            return new X509Certificate2(
                certificate.Export(X509ContentType.Pkcs12, effectivePassword),
                effectivePassword,
                X509KeyStorageFlags.PersistKeySet | X509KeyStorageFlags.Exportable);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to load client certificate");
            throw;
        }
    }

    private void CreateClientCertificate(string certPath, string keyPath, string? password)
    {
        using var rsa = RSA.Create(4096);

        var randomNumber = RandomNumberGenerator.GetInt32(0, 1000);
        var commonName = $"consumer-client-{randomNumber:D3}";
        var subject = new X500DistinguishedName($"CN={commonName}");

        var request = new CertificateRequest(
            subject,
            rsa,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);

        request.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(false, false, 0, true));

        request.CertificateExtensions.Add(
            new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
                true));

        request.CertificateExtensions.Add(
            new X509SubjectKeyIdentifierExtension(request.PublicKey, false));

        var notBefore = DateTimeOffset.UtcNow.AddMinutes(-5);
        var notAfter = DateTimeOffset.UtcNow.AddYears(1);

        using var certificate = request.CreateSelfSigned(notBefore, notAfter);

        var certPem = certificate.ExportCertificatePem();
        var keyPem = string.IsNullOrWhiteSpace(password)
            ? rsa.ExportPkcs8PrivateKeyPem()
            : rsa.ExportEncryptedPkcs8PrivateKeyPem(
                password,
                new PbeParameters(
                    PbeEncryptionAlgorithm.Aes256Cbc,
                    HashAlgorithmName.SHA256,
                    100_000));

        File.WriteAllText(certPath, certPem);
        File.WriteAllText(keyPath, keyPem);

        _logger.LogInformation(
            "Created new self-signed client certificate with CN {CommonName} at {CertificatePath}. Private key encrypted: {IsEncrypted}",
            commonName,
            certPath,
            !string.IsNullOrWhiteSpace(password));
    }

    private static string ResolvePath(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new InvalidOperationException("Certificate path is not configured");
        }

        if (Path.IsPathRooted(path))
        {
            return path;
        }

        return Path.Combine(AppContext.BaseDirectory, path);
    }

    private static void EnsureDirectoryExists(string filePath)
    {
        var directory = Path.GetDirectoryName(filePath);

        if (!string.IsNullOrWhiteSpace(directory) && !Directory.Exists(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }
}