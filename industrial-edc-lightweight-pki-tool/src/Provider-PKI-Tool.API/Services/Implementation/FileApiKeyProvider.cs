using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace Provider_PKI_Tool.API.Services.Implementation;

public class FileApiKeyProvider : IApiKeyProvider
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<FileApiKeyProvider> _logger;
    private List<string> _cachedKeys;
    private DateTime _lastLoadTime;
    private readonly TimeSpan _cacheExpiry = TimeSpan.FromMinutes(5); // Reload every 5 mins

    public FileApiKeyProvider(IConfiguration configuration, ILogger<FileApiKeyProvider> logger)
    {
        _configuration = configuration;
        _logger = logger;
        _cachedKeys = new List<string>();
        _lastLoadTime = DateTime.MinValue;
    }

    public async Task<IEnumerable<string>> GetValidApiKeysAsync()
    {
        // Check if cache is still valid
        if (_cachedKeys.Any() && DateTime.UtcNow - _lastLoadTime < _cacheExpiry)
        {
            return _cachedKeys;
        }

        var filePath = _configuration["ApiKeys:FilePath"];

        if (string.IsNullOrEmpty(filePath))
        {
            _logger.LogWarning("ApiKeys:FilePath is not configured");
            return new List<string>();
        }

        if (!File.Exists(filePath))
        {
            _logger.LogError($"API keys file not found: {filePath}");
            return new List<string>();
        }

        try
        {
            var lines = await File.ReadAllLinesAsync(filePath);
            _cachedKeys = lines
                .Where(line => !string.IsNullOrWhiteSpace(line) && !line.StartsWith("#"))
                .Select(line => line.Trim())
                .ToList();

            _lastLoadTime = DateTime.UtcNow;
            _logger.LogInformation($"Loaded {_cachedKeys.Count} API keys from {filePath}");

            return _cachedKeys;
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error reading API keys file: {ex.Message}");
            return new List<string>();
        }
    }
}