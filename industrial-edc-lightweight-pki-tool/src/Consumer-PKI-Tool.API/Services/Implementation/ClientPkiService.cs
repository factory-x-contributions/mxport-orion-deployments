using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using Consumer_PKI_Tool.API.Models;
using Microsoft.Extensions.Logging;

namespace Consumer_PKI_Tool.API.Services.Implementation;

using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

public class ClientPkiService : IClientPkiService
{
    private readonly IConfigurationService _configurationService;
    private readonly ICertificateLoaderService _certificateLoaderService;
    private readonly ILogger<ClientPkiService> _logger;

    public ClientPkiService(
        IConfigurationService configurationService,
        ICertificateLoaderService certificateLoaderService,
        ILogger<ClientPkiService> logger)
    {
        _configurationService = configurationService;
        _certificateLoaderService = certificateLoaderService;
        _logger = logger;
    }

    public async Task<string> CreateCsrAsync(string? password = null, CancellationToken cancellationToken = default)
    {
        var keyPath = ResolvePath(_configurationService.ClientCA_PrivateKeyPath);
        var effectivePassword = string.IsNullOrWhiteSpace(password)
            ? null
            : password.Trim();

        var currentCertificate = _certificateLoaderService.LoadClientCertificate(effectivePassword);

        if (!File.Exists(keyPath))
        {
            throw new FileNotFoundException($"Client private key file not found: {keyPath}");
        }

        var keyPem = await File.ReadAllTextAsync(keyPath, cancellationToken);

        using var rsa = RSA.Create();

        if (string.IsNullOrWhiteSpace(effectivePassword))
        {
            rsa.ImportFromPem(keyPem);
        }
        else
        {
            rsa.ImportFromEncryptedPem(keyPem, effectivePassword);
        }

        var request = new CertificateRequest(
            currentCertificate.SubjectName,
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

        // Copy SAN from the currently stored certificate into the CSR
        var sanExtension = currentCertificate.Extensions["2.5.29.17"];
        if (sanExtension is not null)
        {
            request.CertificateExtensions.Add(
                new X509Extension(
                    sanExtension.Oid,
                    sanExtension.RawData,
                    sanExtension.Critical));
        }

        var csrPem = request.CreateSigningRequestPem();

        _logger.LogInformation("CSR created successfully for subject {Subject}", currentCertificate.Subject);

        return csrPem;
    }

    public async Task StoreCertificateAsync(string certificatePem, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(certificatePem))
        {
            throw new InvalidOperationException("Certificate PEM is required.");
        }

        var certPath = ResolvePath(_configurationService.ClientCA_CertificatePath);
        EnsureDirectoryExists(certPath);

        var normalizedPem = NormalizePem(certificatePem);

        _ = X509Certificate2.CreateFromPem(normalizedPem);

        await File.WriteAllTextAsync(certPath, normalizedPem, cancellationToken);

        _logger.LogInformation("Client certificate stored successfully at {CertificatePath}", certPath);
    }

    public async Task<string?> GetCertificatePemAsync(CancellationToken cancellationToken = default)
    {
        var certPath = ResolvePath(_configurationService.ClientCA_CertificatePath);

        if (!File.Exists(certPath))
        {
            return null;
        }

        return await File.ReadAllTextAsync(certPath, cancellationToken);
    }

    public async Task<CreateSelfSignedClientCertificateResponseDto> CreateSelfSignedCertificateAsync(
        CreateSelfSignedClientCertificateRequestDto request,
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var keySize = request.KeySize ?? 4096;
        if (keySize is not (2048 or 3072 or 4096))
        {
            throw new InvalidOperationException("KeySize must be one of: 2048, 3072, 4096.");
        }

        var validForDays = request.ValidForDays ?? 365;
        if (validForDays <= 0)
        {
            throw new InvalidOperationException("ValidForDays must be greater than 0.");
        }

        var effectivePassword = string.IsNullOrWhiteSpace(request.Password)
            ? null
            : request.Password.Trim();

        var subject = BuildSubject(request);

        using var rsa = RSA.Create(keySize);

        var certificateRequest = new CertificateRequest(
            subject,
            rsa,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);

        certificateRequest.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(false, false, 0, true));

        certificateRequest.CertificateExtensions.Add(
            new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
                true));

        certificateRequest.CertificateExtensions.Add(
            new X509EnhancedKeyUsageExtension(
                new OidCollection
                {
                    new("1.3.6.1.5.5.7.3.2")
                },
                true));

        certificateRequest.CertificateExtensions.Add(
            new X509SubjectKeyIdentifierExtension(certificateRequest.PublicKey, false));

        AddSubjectAlternativeNames(certificateRequest, request.SubjectAlternativeNames);
        
        var notBefore = DateTimeOffset.UtcNow.AddMinutes(-5);
        var notAfter = notBefore.AddDays(validForDays);

        using var certificate = certificateRequest.CreateSelfSigned(notBefore, notAfter);

        var certificatePem = certificate.ExportCertificatePem();
        var privateKeyPem = string.IsNullOrWhiteSpace(effectivePassword)
            ? rsa.ExportPkcs8PrivateKeyPem()
            : rsa.ExportEncryptedPkcs8PrivateKeyPem(
                effectivePassword,
                new PbeParameters(
                    PbeEncryptionAlgorithm.Aes256Cbc,
                    HashAlgorithmName.SHA256,
                    100_000));

        if (request.Persist)
        {
            var certPath = ResolvePath(_configurationService.ClientCA_CertificatePath);
            var keyPath = ResolvePath(_configurationService.ClientCA_PrivateKeyPath);

            EnsureDirectoryExists(certPath);
            EnsureDirectoryExists(keyPath);

            await File.WriteAllTextAsync(certPath, NormalizePem(certificatePem), cancellationToken);
            await File.WriteAllTextAsync(keyPath, NormalizePem(privateKeyPem), cancellationToken);

            _logger.LogInformation(
                "Persisted self-signed client certificate and private key for subject {Subject}. Private key encrypted: {IsEncrypted}",
                certificate.Subject,
                !string.IsNullOrWhiteSpace(effectivePassword));
        }

        var response = new CreateSelfSignedClientCertificateResponseDto
        {
            CertificatePem = certificatePem,
            PrivateKeyPem = privateKeyPem,
            Subject = certificate.Subject,
            NotBeforeUtc = notBefore,
            NotAfterUtc = notAfter,
            KeySize = keySize
        };

        _logger.LogInformation(
            "Created self-signed client certificate for subject {Subject}, valid until {NotAfterUtc}",
            response.Subject,
            response.NotAfterUtc);

        return response;
    }

    private static X500DistinguishedName BuildSubject(CreateSelfSignedClientCertificateRequestDto request)
    {
        var commonName = string.IsNullOrWhiteSpace(request.CommonName)
            ? $"consumer-client-{RandomNumberGenerator.GetInt32(0, 1000):D3}"
            : request.CommonName.Trim();

        var subjectParts = new List<string>
        {
            $"CN={EscapeDistinguishedNameValue(commonName)}"
        };

        AppendSubjectPart(subjectParts, "C", NormalizeCountry(request.Country));
        AppendSubjectPart(subjectParts, "ST", request.StateOrProvince);
        AppendSubjectPart(subjectParts, "L", request.Locality);
        AppendSubjectPart(subjectParts, "O", request.Organization);
        AppendSubjectPart(subjectParts, "OU", request.OrganizationalUnit);
        AppendSubjectPart(subjectParts, "E", request.EmailAddress);

        return new X500DistinguishedName(string.Join(", ", subjectParts));
    }
    
private static void AddSubjectAlternativeNames(
        CertificateRequest request,
        IEnumerable<string>? sans)
    {
        if (sans is null) return;
    
        var values = sans
            .Where(x => !string.IsNullOrWhiteSpace(x))
            .Select(x => x.Trim())
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();
    
        if (values.Count == 0) return;
    
        var sanBuilder = new SubjectAlternativeNameBuilder();
    
        foreach (var value in values)
        {
            if (IPAddress.TryParse(value, out var ip))
            {
                sanBuilder.AddIpAddress(ip);
            }
            else if (value.StartsWith("email:", StringComparison.OrdinalIgnoreCase))
            {
                sanBuilder.AddEmailAddress(value["email:".Length..]);
            }
            else if (Uri.TryCreate(value, UriKind.Absolute, out var uri))
            {
                sanBuilder.AddUri(uri);
            }
            else
            {
                sanBuilder.AddDnsName(value);
            }
        }
    
        request.CertificateExtensions.Add(sanBuilder.Build());
    }


    private static void AppendSubjectPart(ICollection<string> subjectParts, string key, string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return;
        }

        subjectParts.Add($"{key}={EscapeDistinguishedNameValue(value.Trim())}");
    }

    private static string? NormalizeCountry(string? country)
    {
        if (string.IsNullOrWhiteSpace(country))
        {
            return null;
        }

        var normalized = country.Trim().ToUpperInvariant();

        if (normalized.Length != 2)
        {
            throw new InvalidOperationException("Country must be a 2-letter country code.");
        }

        return normalized;
    }

    private static string EscapeDistinguishedNameValue(string value)
    {
        return value
            .Replace("\\", "\\\\", StringComparison.Ordinal)
            .Replace(",", "\\,", StringComparison.Ordinal)
            .Replace("+", "\\+", StringComparison.Ordinal)
            .Replace("\"", "\\\"", StringComparison.Ordinal)
            .Replace("<", "\\<", StringComparison.Ordinal)
            .Replace(">", "\\>", StringComparison.Ordinal)
            .Replace(";", "\\;", StringComparison.Ordinal);
    }

    private static string NormalizePem(string pem)
    {
        return pem.Replace("\\n", Environment.NewLine).Trim() + Environment.NewLine;
    }

    private static string ResolvePath(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new InvalidOperationException("Certificate path is not configured.");
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