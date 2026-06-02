namespace Consumer_PKI_Tool.API.Models;

public class CreateClientCsrRequestDto
{
    public string? Password { get; set; }
}

public class ClientCsrResponseDto
{
    public string CsrPem { get; set; } = string.Empty;
}

public class StoreClientCertificateDto
{
    public string CertificatePem { get; set; } = string.Empty;
}

public class ClientCertificateResponseDto
{
    public string CertificatePem { get; set; } = string.Empty;
}

public class CreateSelfSignedClientCertificateRequestDto
{
    public string? CommonName { get; set; }
    public string? Country { get; set; }
    public string? StateOrProvince { get; set; }
    public string? Locality { get; set; }
    public string? Organization { get; set; }
    public string? OrganizationalUnit { get; set; }
    public string? EmailAddress { get; set; }
    public int? ValidForDays { get; set; }
    public int? KeySize { get; set; }
    public bool Persist { get; set; }
    public string? Password { get; set; }
    public IList<string> SubjectAlternativeNames { get; set; } = new List<string>(); 
}

public class CreateSelfSignedClientCertificateResponseDto
{
    public string CertificatePem { get; set; } = string.Empty;
    public string PrivateKeyPem { get; set; } = string.Empty;
    public string Subject { get; set; } = string.Empty;
    public DateTimeOffset NotBeforeUtc { get; set; }
    public DateTimeOffset NotAfterUtc { get; set; }
    public int KeySize { get; set; }
}

public class ErrorResponseDto
{
    public string Message { get; set; } = string.Empty;
    public string? Details { get; set; }
    public string TraceId { get; set; } = string.Empty;
}