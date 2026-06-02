using System.Threading;
using System.Threading.Tasks;
using Consumer_PKI_Tool.API.Models;

namespace Consumer_PKI_Tool.API.Services;

public interface IClientPkiService
{
    Task<string> CreateCsrAsync(string? password = null, CancellationToken cancellationToken = default);
    Task StoreCertificateAsync(string certificatePem, CancellationToken cancellationToken = default);
    Task<string?> GetCertificatePemAsync(CancellationToken cancellationToken = default);
    Task<CreateSelfSignedClientCertificateResponseDto> CreateSelfSignedCertificateAsync(
        CreateSelfSignedClientCertificateRequestDto request,
        CancellationToken cancellationToken = default);
}