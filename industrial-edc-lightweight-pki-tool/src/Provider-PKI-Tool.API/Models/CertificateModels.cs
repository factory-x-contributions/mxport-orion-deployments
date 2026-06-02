using System;

namespace Provider_PKI_Tool.API.Models;

public class CertificateRequestDto
{
    public string CsrPem { get; set; }
    public int ValidityDays { get; set; } = 365;
    public string? CommonName { get; set; }
}

// Models/Requests/CertificateRenewalDto.cs
public class CertificateRenewalDto
{
    public string CertificatePem { get; set; }
    public int ValidityDays { get; set; } = 365;
}

// Models/Requests/CertificateRevocationDto.cs
public class CertificateRevocationDto
{
    public string CertificatePem { get; set; }
    public string? Reason { get; set; }
}

// Models/Requests/CsrValidationDto.cs
public class CsrValidationDto
{
    public string CsrPem { get; set; }
}

// Models/Responses/CertificateResponseDto.cs
public class CertificateResponseDto
{
    public string CertificatePem { get; set; }
    public string SerialNumber { get; set; }
    public DateTime IssuedAt { get; set; }
    public DateTime ExpiresAt { get; set; }
}

// Models/Responses/CaChainResponseDto.cs
public class CaChainResponseDto
{
    public string RootCertificate { get; set; }
    public string IntermediateCertificate { get; set; }
}

// Models/Responses/CsrValidationResponseDto.cs
public class CsrValidationResponseDto
{
    public bool IsValid { get; set; }
    public string? Subject { get; set; }
    public string[]? SubjectAlternativeNames { get; set; }
    public string? ErrorMessage { get; set; }
}

// Models/Responses/ErrorResponseDto.cs
public class ErrorResponseDto
{
    public string Message { get; set; }
    public string? Details { get; set; }
    public string TraceId { get; set; }
}