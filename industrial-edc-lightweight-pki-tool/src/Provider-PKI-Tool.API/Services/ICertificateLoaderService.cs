using System.Security.Cryptography.X509Certificates;

namespace Provider_PKI_Tool.API.Services;

public interface ICertificateLoaderService
{
    X509Certificate2 LoadIntermediateCertificate();
    X509Certificate2 LoadRootCertificate();
}