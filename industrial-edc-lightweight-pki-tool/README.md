# EDC Lightweight PKI Tool

> ⚠️ **Disclaimer — Not for Production Use**
> This tool is designed exclusively for **local development and cloud-agnostic integration testing**. It intentionally omits production-grade concerns such as HSM-backed key storage, CRL/OCSP publishing, certificate revocation persistence, high availability, and security hardening. **Do not deploy this tool in a production environment or expose it to untrusted networks.** For production workloads, replace it with a managed PKI service (see [Replacing with Cloud PKI Services](#replacing-with-cloud-pki-services)).

A lightweight, self-hosted Public Key Infrastructure (PKI) solution built with **.NET 9 / ASP.NET Core**. It is designed to bootstrap certificate-based mutual-TLS (mTLS) trust between **Eclipse Dataspace Components (EDC)** connectors — a Provider and a Consumer — without requiring an enterprise CA or a cloud PKI service.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Projects](#projects)
4. [API Reference](#api-reference)
5. [Configuration](#configuration)
6. [Running Locally](#running-locally)
7. [Docker](#docker)
8. [Authentication](#authentication)
9. [Replacing with Cloud PKI Services](#replacing-with-cloud-pki-services)
10. [Known Limitations](#known-limitations)

---

## Overview

In an EDC dataspace, every connector must prove its identity through an X.509 certificate. This tool provides two small HTTP APIs that handle the entire certificate lifecycle:

| Concern | Component |
|---|---|
| Issue, renew, revoke and validate certificates | **Provider PKI Tool API** |
| Generate CSRs, store/retrieve certificates, create self-signed certs | **Consumer PKI Tool API** |

Both APIs are stateless, container-friendly, and protected by API-key authentication.

---

## Architecture

```
┌─────────────────────────────────┐        ┌─────────────────────────────────┐
│      Provider PKI Tool API      │        │      Consumer PKI Tool API      │
│                                 │        │                                 │
│  Root CA  ──►  Intermediate CA  │        │  Client Key + Certificate Store │
│                    │            │        │                                 │
│  POST /api/pki/certificates/    │◄───────│  POST /api/clientpki/csr        │
│         request (sign CSR)      │        │  POST /api/clientpki/certificate│
│  POST /api/pki/certificates/    │        │  GET  /api/clientpki/certificate│
│         renew                   │        │  POST /api/clientpki/           │
│  POST /api/pki/certificates/    │        │       self-signed-certificate   │
│         revoke                  │        └─────────────────────────────────┘
│  GET  /api/pki/ca-chain         │
│  POST /api/pki/csr/validate     │
└─────────────────────────────────┘
```

**Certificate signing flow:**

1. Consumer calls `POST /api/clientpki/csr` → receives a PEM-encoded CSR.
2. Consumer (or orchestrator) forwards the CSR to `POST /api/pki/certificates/request` on the Provider side.
3. Provider signs the CSR with the Intermediate CA and returns the signed certificate PEM.
4. Consumer stores the certificate via `POST /api/clientpki/certificate`.

---

## Projects

| Project | Description |
|---|---|
| `Provider-PKI-Tool.API` | CA-side service. Loads Root CA + Intermediate CA from PEM files, signs CSRs, renews and revokes certificates, exposes the public CA chain. |
| `Consumer-PKI-Tool.API` | Client-side service. Manages the consumer's own key pair and certificate. Can also generate a self-signed certificate for initial bootstrapping. |

Both projects use:
- **BouncyCastle** (`Org.BouncyCastle`) for low-level crypto operations.
- **ASP.NET Core** minimal hosting with Swagger/OpenAPI (available in Development mode).
- **File-based API key provider** for authentication.

---

## API Reference

### Provider PKI Tool API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/pki/certificates/request` | Required | Sign a CSR and issue a certificate |
| `POST` | `/api/pki/certificates/renew` | Required | Renew an existing certificate |
| `POST` | `/api/pki/certificates/revoke` | Required | Revoke a certificate |
| `GET`  | `/api/pki/ca-chain` | None | Retrieve Root + Intermediate CA certificates |
| `POST` | `/api/pki/csr/validate` | Required | Validate a CSR |
| `GET`  | `/health` | None | Health check |

#### Sign a CSR — request body
```json
{
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----\n...",
  "validityDays": 365
}
```

#### Sign a CSR — response body
```json
{
  "certificatePem": "-----BEGIN CERTIFICATE-----\n...",
  "serialNumber": "...",
  "issuedAt": "2026-04-27T00:00:00Z",
  "expiresAt": "2027-04-27T00:00:00Z"
}
```

---

### Consumer PKI Tool API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/clientpki/csr` | Required | Generate a CSR from the stored key |
| `POST` | `/api/clientpki/self-signed-certificate` | Required | Create a self-signed certificate + key pair |
| `POST` | `/api/clientpki/certificate` | Required | Store a signed certificate returned by the Provider |
| `GET`  | `/api/clientpki/certificate` | Required | Retrieve the currently stored certificate |
| `GET`  | `/health` | None | Health check |

#### Create self-signed certificate — request body
```json
{
  "commonName": "consumer-connector",
  "country": "DE",
  "organization": "ACME Corp",
  "validForDays": 365,
  "keySize": 4096,
  "persist": true,
  "subjectAlternativeNames": ["consumer.example.com"]
}
```

---

## Configuration

Configuration is provided through `appsettings.json` / `appsettings.Development.json` or environment variables.

### Provider PKI Tool — `appsettings.Development.json`

```json
{
  "Pki": {
    "RootCA": {
      "CertificatePath": "./certs/rootca/root-ca.cert.pem",
      "PrivateKeyPath":  "./certs/rootca/root-ca.key.pem",
      "Password": "<key-password>",
      "Source": "FilePath"
    },
    "IntermediateCA": {
      "CertificatePath": "./certs/intermediate/intermediate.cert.pem",
      "PrivateKeyPath":  "./certs/intermediate/intermediate.key.pem",
      "Password": "<key-password>",
      "Source": "FilePath"
    }
  },
  "ApiKeys": {
    "FilePath": "./Files.Dev/api-keys.txt"
  }
}
```

### Consumer PKI Tool — `appsettings.Development.json`

```json
{
  "Pki": {
    "ClientCA": {
      "CertificatePath": "./certs/client/client-cert.pem",
      "PrivateKeyPath":  "./certs/client/client-key.pem",
      "Password": "<key-password>",
      "Source": "FilePath"
    }
  },
  "ApiKeys": {
    "FilePath": "./Files.Dev/api-keys.txt"
  }
}
```

> **Tip:** Use environment variables (e.g. `PKI__RootCA__Password`) to avoid storing credentials in config files.

---

## Running Locally

**Prerequisites:** .NET 9 SDK

```bash
# Provider
cd Provider-PKI-Tool.API
dotnet run

# Consumer (separate terminal)
cd Consumer-PKI-Tool.API
dotnet run
```

Swagger UI is available at `http://localhost:<port>` when running in Development mode.

---

## Docker

Each project contains its own `Dockerfile`. Build and run:

```bash
# Provider
docker build -f Provider-PKI-Tool.API/Dockerfile -t provider-pki-tool .
docker run -p 8080:8080 \
  -e PKI__RootCA__CertificatePath=/certs/root-ca.cert.pem \
  -e PKI__RootCA__PrivateKeyPath=/certs/root-ca.key.pem \
  -e PKI__RootCA__Password=<password> \
  -e PKI__IntermediateCA__CertificatePath=/certs/intermediate.cert.pem \
  -e PKI__IntermediateCA__PrivateKeyPath=/certs/intermediate.key.pem \
  -e PKI__IntermediateCA__Password=<password> \
  -v /path/to/certs:/certs \
  provider-pki-tool

# Consumer
docker build -f Consumer-PKI-Tool.API/Dockerfile -t consumer-pki-tool .
docker run -p 8082:8080 \
  -e PKI__ClientCA__CertificatePath=/certs/client-cert.pem \
  -e PKI__ClientCA__PrivateKeyPath=/certs/client-key.pem \
  -v /path/to/certs:/certs \
  consumer-pki-tool
```

---

## Authentication

Both APIs use **API Key authentication** via the `X-API-Key` HTTP header.

Keys are stored one-per-line in a plain-text file (path configured by `ApiKeys:FilePath`).

```
# Files.Dev/api-keys.txt
27603b43-eba0-4997-b128-3ae262076e5b
965e6148-d7ed-483b-89df-cca0f8cdb331
```

> ⚠️ The keys in `Files.Dev/` are for **local development only**. Replace them with secrets management (e.g. environment variables, Vault, Azure Key Vault) in production.

---

## Replacing with Cloud PKI Services

This tool is intentionally lightweight and intended for development, testing, or small-scale deployments. When you need higher availability, auditing, compliance, or HSM-backed keys, it can be replaced by a managed cloud PKI service. The table below maps each capability to its cloud equivalent.

### AWS — AWS Private Certificate Authority (Private CA)

| This tool | AWS Private CA equivalent |
|---|---|
| Sign CSR (`POST /certificates/request`) | [`IssueCertificate`](https://docs.aws.amazon.com/acm-pca/latest/APIReference/API_IssueCertificate.html) API |
| Revoke certificate | [`RevokeCertificate`](https://docs.aws.amazon.com/acm-pca/latest/APIReference/API_RevokeCertificate.html) API |
| CA chain (`GET /ca-chain`) | [`GetCertificateAuthorityCertificate`](https://docs.aws.amazon.com/acm-pca/latest/APIReference/API_GetCertificateAuthorityCertificate.html) |
| File-based key storage | AWS KMS (HSM-backed) |
| File-based API keys | AWS IAM / Cognito |

**Migration steps:**
1. Create a Private CA hierarchy in AWS Private CA (Root → Subordinate).
2. Replace `PkiService.SignCsrAsync` with a call to `IssueCertificate` using the AWS SDK (`AWSSDK.ACMPCA`).
3. Replace `RevokeCertificateAsync` with `RevokeCertificate`.
4. Replace `GetCaChainAsync` with `GetCertificateAuthorityCertificate`.
5. Remove the `CertificateLoaderService` and configure the CA ARN via environment variable.

---

### Azure — Azure Key Vault Certificates

| This tool | Azure Key Vault equivalent |
|---|---|
| Sign CSR | `CertificateClient.MergeCertificateAsync` (bring your own CA flow) or Key Vault integrated CA |
| Issue certificate | `CertificateClient.StartCreateCertificateAsync` with a `CertificatePolicy` |
| Revoke / delete | `CertificateClient.DeleteCertificateAsync` + CRL via DigiCert / GlobalSign integration |
| CA chain | Key Vault stores the full chain automatically |
| File-based key storage | Key Vault Secrets / HSM-backed keys |
| File-based API keys | Azure AD / Managed Identity |

**Migration steps:**
1. Create an Azure Key Vault and configure a Certificate Issuer (DigiCert, GlobalSign, or `Self`).
2. Replace `PkiService` with calls to the `Azure.Security.KeyVault.Certificates` NuGet package.
3. For the Consumer side, generate the CSR via `CertificateClient.StartCreateCertificateAsync` and retrieve the pending CSR bytes; merge the signed certificate back with `MergeCertificateAsync`.
4. Use Azure Managed Identity or `DefaultAzureCredential` instead of API keys.

---

### Google Cloud — Certificate Authority Service (CAS)

| This tool | Google CAS equivalent |
|---|---|
| Sign CSR | [`projects.locations.caPools.certificates.create`](https://cloud.google.com/certificate-authority-service/docs/reference/rest/v1/projects.locations.caPools.certificates/create) |
| Revoke certificate | [`projects.locations.caPools.certificates.revoke`](https://cloud.google.com/certificate-authority-service/docs/reference/rest/v1/projects.locations.caPools.certificates/revoke) |
| CA chain | Returned automatically in the `CreateCertificate` response |
| File-based key storage | Cloud HSM / Cloud KMS |
| File-based API keys | Google IAM / Service Accounts |

**Migration steps:**
1. Create a CA Pool and a Root/Subordinate CA in Google CAS.
2. Replace `PkiService.SignCsrAsync` with `CertificateAuthorityServiceClient.CreateCertificate` from the `Google.Cloud.Security.PrivateCA.V1` NuGet package.
3. Pass the raw CSR PEM as `pemCsr` in the `Certificate` resource.
4. Use Application Default Credentials (`GoogleCredential.GetApplicationDefault()`) instead of API keys.

---

### HashiCorp Vault PKI Secrets Engine

| This tool | Vault PKI equivalent |
|---|---|
| Sign CSR | `POST /pki/sign/<role>` |
| Issue certificate | `POST /pki/issue/<role>` |
| Revoke certificate | `POST /pki/revoke` |
| CA chain | `GET /pki/ca_chain` |
| File-based key storage | Vault Transit / HSM seal |
| File-based API keys | Vault Token / AppRole / Kubernetes Auth |

**Migration steps:**
1. Enable and configure the PKI secrets engine (`vault secrets enable pki`).
2. Replace `PkiService` with HTTP calls to the Vault API (or use the `VaultSharp` NuGet package).
3. Configure roles that define allowed domains, key sizes, and TTLs.
4. Replace file-based API key loading with Vault AppRole or Kubernetes service account authentication.

---

## Known Limitations

- **No CRL / OCSP endpoint** — certificate revocation is logged but not persisted or published. Integrate a CRL distribution point or OCSP responder for production use.
- **In-memory CA state** — the CA certificates are loaded at startup. Rotation requires a restart.
- **File-based key storage** — private keys are stored as PEM files on disk. Use a secrets manager or HSM for production.
- **Single-node** — there is no replication or HA mechanism; run behind a load balancer with shared storage for HA deployments.

