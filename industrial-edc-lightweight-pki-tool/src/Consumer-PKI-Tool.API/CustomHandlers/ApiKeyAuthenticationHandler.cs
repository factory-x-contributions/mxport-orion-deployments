using System;
using System.Linq;
using System.Security.Claims;
using System.Text.Encodings.Web;
using System.Threading.Tasks;
using Consumer_PKI_Tool.API.Services;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

public class ApiKeyAuthenticationHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    private const string ApiKeyHeaderName = "X-API-Key";
    private readonly IApiKeyProvider _apiKeyProvider;

    public ApiKeyAuthenticationHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder,
        IApiKeyProvider apiKeyProvider) 
        : base(options, logger, encoder)
    {
        _apiKeyProvider = apiKeyProvider;
    }

    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!Request.Headers.TryGetValue(ApiKeyHeaderName, out var apiKeyHeaderValues))
        {
            return AuthenticateResult.NoResult();
        }

        var providedApiKey = apiKeyHeaderValues.ToString();

        try
        {
            var validApiKeys = await _apiKeyProvider.GetValidApiKeysAsync();

            if (validApiKeys.Contains(providedApiKey))
            {
                var claims = new[] 
                { 
                    new Claim(ClaimTypes.NameIdentifier, providedApiKey),
                    new Claim("ApiKey", MaskApiKey(providedApiKey))
                };
                var identity = new ClaimsIdentity(claims, Scheme.Name);
                var principal = new ClaimsPrincipal(identity);
                var ticket = new AuthenticationTicket(principal, Scheme.Name);

                return AuthenticateResult.Success(ticket);
            }

            return AuthenticateResult.Fail("Invalid API Key");
        }
        catch (Exception ex)
        {
            Logger.LogError($"Error validating API key: {ex.Message}");
            return AuthenticateResult.Fail("Authentication failed");
        }
    }

    private string MaskApiKey(string apiKey)
    {
        if (apiKey.Length <= 4)
            return "****";
        
        return $"{apiKey.Substring(0, 4)}...{apiKey.Substring(apiKey.Length - 4)}";
    }
}