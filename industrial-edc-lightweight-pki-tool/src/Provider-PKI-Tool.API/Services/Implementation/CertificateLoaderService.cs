using System;
using System.IO;
using Microsoft.Extensions.Logging;

namespace Provider_PKI_Tool.API.Services.Implementation;

using System.Security.Cryptography.X509Certificates;
using Org.BouncyCastle.OpenSsl;
using Org.BouncyCastle.Pkcs;
using Org.BouncyCastle.X509;

public class CertificateLoaderService : ICertificateLoaderService
{
    private readonly IConfigurationService _configurationService;
    private readonly ILogger<CertificateLoaderService> _logger;

    public CertificateLoaderService(IConfigurationService configurationService, ILogger<CertificateLoaderService> logger)
    {
        _configurationService = configurationService;
        _logger = logger;
    }

    public X509Certificate2 LoadIntermediateCertificate()
    {
        try
        {
            var certPath = ResolvePath(_configurationService.IntermediateCA_CertificatePath);
            var keyPath = ResolvePath(_configurationService.IntermediateCA_PrivateKeyPath);
            var password = _configurationService.IntermediateCA_Password;

            _logger.LogInformation($"Loading intermediate certificate from {certPath}");
            return LoadPemCertificateWithKey(certPath, keyPath, password);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Failed to load intermediate certificate: {ex.Message}");
            throw;
        }
    }

    public X509Certificate2 LoadRootCertificate()
    {
        try
        {
            var certPath = ResolvePath(_configurationService.RootCA_CertificatePath);
            var keyPath = ResolvePath(_configurationService.RootCA_PrivateKeyPath);
            var password = _configurationService.RootCA_Password;

            _logger.LogInformation($"Loading root certificate from {certPath}");
            return LoadPemCertificateWithKey(certPath, keyPath, password);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Failed to load root certificate: {ex.Message}");
            throw;
        }
    }

    private string ResolvePath(string path)
    {
        if (string.IsNullOrEmpty(path))
        {
            throw new InvalidOperationException("Certificate path is not configured");
        }

        // If path is already absolute, return as-is
        if (Path.IsPathRooted(path))
        {
            return path;
        }

        // Resolve relative to application base directory
        return Path.Combine(AppContext.BaseDirectory, path);
    }

    private X509Certificate2 LoadPemCertificateWithKey(string certPath, string keyPath, string password)
    {
        if (!File.Exists(certPath))
        {
            throw new FileNotFoundException($"Certificate file not found: {certPath}");
        }

        if (!File.Exists(keyPath))
        {
            throw new FileNotFoundException($"Private key file not found: {keyPath}");
        }

        try
        {
            // Read PEM files
            var certPem = File.ReadAllText(certPath);
            var keyPem = File.ReadAllText(keyPath);

            // Parse certificate using BouncyCastle
            Org.BouncyCastle.X509.X509Certificate bcCert;
            using (var certReader = new StringReader(certPem))
            {
                var pemReader = new PemReader(certReader);
                var certObj = pemReader.ReadObject();
                bcCert = (Org.BouncyCastle.X509.X509Certificate)certObj ?? throw new InvalidOperationException("Failed to parse certificate");
            }

            // Parse private key using BouncyCastle
            Org.BouncyCastle.Crypto.AsymmetricKeyParameter privateKey;
            using (var keyReader = new StringReader(keyPem))
            {
                var passwordFinder = new PasswordFinder(password);
                var pemReader = new PemReader(keyReader, passwordFinder);
                var keyObj = pemReader.ReadObject();

                if (keyObj is Org.BouncyCastle.Crypto.AsymmetricKeyParameter asym)
                {
                    privateKey = asym;
                }
                else if (keyObj is AsymmetricKeyEntry keyEntry)
                {
                    privateKey = keyEntry.Key;
                }
                else
                {
                    throw new InvalidOperationException("Failed to parse private key");
                }
            }
            
            // Create PKCS12 store with certificate and private key
            var store = new Pkcs12StoreBuilder()
                .Build();
    
            var certEntry = new X509CertificateEntry(bcCert);
            var alias = bcCert.SubjectDN.ToString();

            store.SetCertificateEntry(alias, certEntry);
            store.SetKeyEntry(alias, new AsymmetricKeyEntry(privateKey), new[] { certEntry });

            // Convert to .NET X509Certificate2
            using (var stream = new MemoryStream())
            {
                var passwordBytes = string.IsNullOrEmpty(password) 
                    ? Array.Empty<char>() 
                    : password.ToCharArray();
        
                store.Save(stream, passwordBytes, new Org.BouncyCastle.Security.SecureRandom());
    
                var cert = new X509Certificate2(
                    stream.ToArray(), 
                    password,
                    X509KeyStorageFlags.PersistKeySet | X509KeyStorageFlags.Exportable);

                _logger.LogInformation($"Successfully loaded PEM certificate. Subject: {cert.SubjectName.Name}, Serial: {cert.SerialNumber}");
                return cert;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error loading PEM certificate from {certPath}: {ex.Message}");
            throw new InvalidOperationException($"Failed to load certificate from {certPath}", ex);
        }
    }

    /// <summary>
    /// Password finder for BouncyCastle PEM reader
    /// </summary>
    private class PasswordFinder : IPasswordFinder
    {
        private readonly string _password;

        public PasswordFinder(string password)
        {
            _password = password;
        }

        public char[] GetPassword()
        {
            return string.IsNullOrEmpty(_password) 
                ? Array.Empty<char>() 
                : _password.ToCharArray();
        }
    }
}