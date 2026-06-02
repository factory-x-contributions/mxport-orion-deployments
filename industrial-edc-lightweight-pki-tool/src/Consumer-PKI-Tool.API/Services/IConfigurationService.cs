namespace Consumer_PKI_Tool.API.Services;

public interface IConfigurationService
{
    string ClientCA_CertificatePath { get; }
    string ClientCA_PrivateKeyPath { get; }
    string ClientCA_Password { get; }
}