// Controllers/PkiController.cs

using System;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;
using Provider_PKI_Tool.API.Models;
using Provider_PKI_Tool.API.Services;

namespace Provider_PKI_Tool.API.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class PkiController : ControllerBase
{
    private readonly IPkiService _pkiService;
    private readonly ILogger<PkiController> _logger;

    public PkiController(IPkiService pkiService, ILogger<PkiController> logger)
    {
        _pkiService = pkiService;
        _logger = logger;
    }

    /// <summary>
    /// Sign a Certificate Signing Request (CSR) and issue a certificate
    /// </summary>
    [HttpPost("certificates/request")]
    [ProducesResponseType(typeof(CertificateResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> RequestCertificate([FromBody] CertificateRequestDto request)
    {
        try
        {
            _logger.LogInformation("Received certificate request");

            if (string.IsNullOrWhiteSpace(request.CsrPem))
            {
                return BadRequest(new ErrorResponseDto
                {
                    Message = "CSR is required",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            var result = await _pkiService.SignCsrAsync(request.CsrPem, request.ValidityDays);
            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error processing certificate request: {ex.Message}");
            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to process certificate request",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    /// <summary>
    /// Renew an existing certificate
    /// </summary>
    [HttpPost("certificates/renew")]
    [ProducesResponseType(typeof(CertificateResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> RenewCertificate([FromBody] CertificateRenewalDto request)
    {
        try
        {
            _logger.LogInformation("Received certificate renewal request");

            if (string.IsNullOrWhiteSpace(request.CertificatePem))
            {
                return BadRequest(new ErrorResponseDto
                {
                    Message = "Certificate is required",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            var result = await _pkiService.RenewCertificateAsync(request.CertificatePem, request.ValidityDays);
            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error renewing certificate: {ex.Message}");
            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to renew certificate",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    /// <summary>
    /// Revoke a certificate
    /// </summary>
    [HttpPost("certificates/revoke")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> RevokeCertificate([FromBody] CertificateRevocationDto request)
    {
        try
        {
            _logger.LogInformation("Received certificate revocation request");

            if (string.IsNullOrWhiteSpace(request.CertificatePem))
            {
                return BadRequest(new ErrorResponseDto
                {
                    Message = "Certificate is required",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            await _pkiService.RevokeCertificateAsync(request.CertificatePem, request.Reason);
            return NoContent();
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error revoking certificate: {ex.Message}");
            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to revoke certificate",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    /// <summary>
    /// Get the CA chain (Root and Intermediate certificates)
    /// </summary>
    [HttpGet("ca-chain")]
    [AllowAnonymous]
    [ProducesResponseType(typeof(CaChainResponseDto), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetCaChain()
    {
        try
        {
            var result = await _pkiService.GetCaChainAsync();
            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error retrieving CA chain: {ex.Message}");
            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to retrieve CA chain",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    /// <summary>
    /// Validate a Certificate Signing Request (CSR)
    /// </summary>
    [HttpPost("csr/validate")]
    [ProducesResponseType(typeof(CsrValidationResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> ValidateCsr([FromBody] CsrValidationDto request)
    {
        try
        {
            _logger.LogInformation("Received CSR validation request");

            if (string.IsNullOrWhiteSpace(request.CsrPem))
            {
                return BadRequest(new ErrorResponseDto
                {
                    Message = "CSR is required",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            var result = await _pkiService.ValidateCsrAsync(request.CsrPem);
            return Ok(result);
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error validating CSR: {ex.Message}");
            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to validate CSR",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }
}