using System.Security.Cryptography.X509Certificates;

namespace Consumer_PKI_Tool.API.Services;

public interface ICertificateLoaderService
{
    X509Certificate2 LoadClientCertificate(string? password = null);
}