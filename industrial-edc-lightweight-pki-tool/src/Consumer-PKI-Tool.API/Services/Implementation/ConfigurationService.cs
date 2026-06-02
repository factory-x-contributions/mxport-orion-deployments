using Microsoft.Extensions.Configuration;

namespace Consumer_PKI_Tool.API.Services.Implementation;

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

    public string ClientCA_CertificatePath => _configuration.GetValue<string>("PKI:ClientCA:CertificatePath")  ?? string.Empty;
    
    public string ClientCA_PrivateKeyPath => _configuration.GetValue<string>("PKI:ClientCA:PrivateKeyPath") ?? string.Empty;
    
    public string ClientCA_Password => _configuration.GetValue<string>("PKI:ClientCA:Password") ?? string.Empty;
    
    #endregion
}