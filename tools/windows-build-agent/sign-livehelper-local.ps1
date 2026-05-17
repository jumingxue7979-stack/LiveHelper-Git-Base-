$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $Root "LiveHelperWindowsObsRank.exe"
$Signed = Join-Path $Root "LiveHelperWindowsObsRank_signed.exe"
$Backup = Join-Path $Root "LiveHelperWindowsObsRank_unsigned_backup.exe"

if (-not (Test-Path -LiteralPath $Source)) {
  throw "Source EXE not found: $Source"
}

if (-not (Test-Path -LiteralPath $Backup)) {
  Copy-Item -LiteralPath $Source -Destination $Backup -Force
}

Copy-Item -LiteralPath $Source -Destination $Signed -Force

$cert = Get-ChildItem Cert:\CurrentUser\My |
  Where-Object { $_.Subject -eq "CN=LiveHelper Local Code Signing" -and $_.HasPrivateKey } |
  Select-Object -First 1

if (-not $cert) {
  $cert = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject "CN=LiveHelper Local Code Signing" `
    -CertStoreLocation Cert:\CurrentUser\My `
    -KeyExportPolicy Exportable `
    -KeyUsage DigitalSignature `
    -HashAlgorithm SHA256
}

function Add-ToStoreIfMissing {
  param(
    [string] $StoreName,
    [System.Security.Cryptography.X509Certificates.X509Certificate2] $Certificate
  )

  $store = New-Object System.Security.Cryptography.X509Certificates.X509Store(
    $StoreName,
    [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser
  )
  $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
  try {
    $found = $false
    foreach ($existing in $store.Certificates) {
      if ($existing.Thumbprint -eq $Certificate.Thumbprint) {
        $found = $true
        break
      }
    }

    if (-not $found) {
      $store.Add($Certificate)
    }
  } finally {
    $store.Close()
  }
}

Add-ToStoreIfMissing -StoreName "Root" -Certificate $cert
Add-ToStoreIfMissing -StoreName "TrustedPublisher" -Certificate $cert

$signResult = Set-AuthenticodeSignature -FilePath $Signed -Certificate $cert -HashAlgorithm SHA256
$embeddedCert = (Get-AuthenticodeSignature -FilePath $Signed).SignerCertificate
if ($embeddedCert) {
  Add-ToStoreIfMissing -StoreName "Root" -Certificate $embeddedCert
  Add-ToStoreIfMissing -StoreName "TrustedPublisher" -Certificate $embeddedCert
}

$signature = Get-AuthenticodeSignature -FilePath $Signed
$hash = Get-FileHash -LiteralPath $Signed -Algorithm SHA256

[pscustomobject]@{
  SignedExe = $Signed
  SignatureStatus = $signature.Status
  SignatureStatusMessage = $signature.StatusMessage
  Signer = $signature.SignerCertificate.Subject
  Thumbprint = $signature.SignerCertificate.Thumbprint
  Sha256 = $hash.Hash
}
