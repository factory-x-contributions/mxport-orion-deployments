namespace Provider_PKI_Tool.API.Services;

public interface IConfigurationService
{
    string RootCA_CertificatePath { get; }
    string RootCA_PrivateKeyPath { get; }
    string RootCA_Password { get; }
    string IntermediateCA_CertificatePath { get; }
    string IntermediateCA_PrivateKeyPath { get; }
    string IntermediateCA_Password { get; }
}