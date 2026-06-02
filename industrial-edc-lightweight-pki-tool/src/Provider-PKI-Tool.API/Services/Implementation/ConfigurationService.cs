using Microsoft.Extensions.Configuration;

namespace Provider_PKI_Tool.API.Services.Implementation;

public class ConfigurationService : IConfigurationService
{
    #region Private

    private readonly ConfigurationManager _configuration;

    #endregion

    #region Public

    public ConfigurationService(ConfigurationManager configuration)
    {
        _configuration = configuration;
    }

    #endregion
    
    #region IConfigurationService

    public string RootCA_CertificatePath => _configuration.GetValue<string>("PKI:RootCA:CertificatePath") ?? string.Empty;
    
    public string RootCA_PrivateKeyPath => _configuration.GetValue<string>("PKI:RootCA:PrivateKeyPath") ?? string.Empty;
    
    public string RootCA_Password => _configuration.GetValue<string>("PKI:RootCA:Password") ?? string.Empty;
    
    public string IntermediateCA_CertificatePath => _configuration.GetValue<string>("PKI:IntermediateCA:CertificatePath") ?? string.Empty;
    
    public string IntermediateCA_PrivateKeyPath => _configuration.GetValue<string>("PKI:IntermediateCA:PrivateKeyPath") ?? string.Empty;
    
    public string IntermediateCA_Password => _configuration.GetValue<string>("PKI:IntermediateCA:Password") ?? string.Empty;
    
    #endregion
}