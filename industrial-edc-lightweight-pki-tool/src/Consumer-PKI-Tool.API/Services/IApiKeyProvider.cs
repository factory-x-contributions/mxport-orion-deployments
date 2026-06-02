using System.Collections.Generic;
using System.Threading.Tasks;

namespace Consumer_PKI_Tool.API.Services;

public interface IApiKeyProvider
{
    Task<IEnumerable<string>> GetValidApiKeysAsync();
}