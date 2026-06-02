using System.Threading.Tasks;
using Provider_PKI_Tool.API.Models;

namespace Provider_PKI_Tool.API.Services;

public interface IPkiService
{
    Task<CertificateResponseDto> SignCsrAsync(string csrPem, int validityDays);
    Task<CertificateResponseDto> RenewCertificateAsync(string certificatePem, int validityDays);
    Task RevokeCertificateAsync(string certificatePem, string? reason);
    Task<CaChainResponseDto> GetCaChainAsync();
    Task<CsrValidationResponseDto> ValidateCsrAsync(string csrPem);
}