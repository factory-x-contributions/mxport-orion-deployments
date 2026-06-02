using System;
using System.Threading;
using System.Threading.Tasks;
using Consumer_PKI_Tool.API.Models;
using Consumer_PKI_Tool.API.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;

namespace Consumer_PKI_Tool.API.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class ClientPkiController : BaseController
{
    private readonly IClientPkiService _clientPkiService;
    private readonly ILogger<ClientPkiController> _logger;

    public ClientPkiController(
        IClientPkiService clientPkiService,
        ILogger<ClientPkiController> logger)
    {
        _clientPkiService = clientPkiService;
        _logger = logger;
    }

    [HttpPost("csr")]
    [ProducesResponseType(typeof(ClientCsrResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> CreateCsr(
        [FromBody] CreateClientCsrRequestDto? request,
        CancellationToken cancellationToken)
    {
        try
        {
            var csrPem = await _clientPkiService.CreateCsrAsync(
                request?.Password,
                cancellationToken);

            return Ok(new ClientCsrResponseDto
            {
                CsrPem = csrPem
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to create CSR");

            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to create CSR",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    [HttpPost("self-signed-certificate")]
    [ProducesResponseType(typeof(CreateSelfSignedClientCertificateResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> CreateSelfSignedCertificate(
        [FromBody] CreateSelfSignedClientCertificateRequestDto request,
        CancellationToken cancellationToken)
    {
        try
        {
            var response = await _clientPkiService.CreateSelfSignedCertificateAsync(
                request ?? new CreateSelfSignedClientCertificateRequestDto(),
                cancellationToken);

            return Ok(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to create self-signed client certificate");

            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to create self-signed client certificate",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    [HttpPost("certificate")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> StoreCertificate(
        [FromBody] StoreClientCertificateDto request,
        CancellationToken cancellationToken)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(request.CertificatePem))
            {
                return BadRequest(new ErrorResponseDto
                {
                    Message = "CertificatePem is required",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            await _clientPkiService.StoreCertificateAsync(request.CertificatePem, cancellationToken);

            return NoContent();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to store certificate");

            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to store certificate",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }

    [HttpGet("certificate")]
    [ProducesResponseType(typeof(ClientCertificateResponseDto), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponseDto), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetCertificate(CancellationToken cancellationToken)
    {
        try
        {
            var certificatePem = await _clientPkiService.GetCertificatePemAsync(cancellationToken);

            if (string.IsNullOrWhiteSpace(certificatePem))
            {
                return NotFound(new ErrorResponseDto
                {
                    Message = "Client certificate not found",
                    TraceId = HttpContext.TraceIdentifier
                });
            }

            return Ok(new ClientCertificateResponseDto
            {
                CertificatePem = certificatePem
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to get certificate");

            return BadRequest(new ErrorResponseDto
            {
                Message = "Failed to get certificate",
                Details = ex.Message,
                TraceId = HttpContext.TraceIdentifier
            });
        }
    }
}