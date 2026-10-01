$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$shopName = Read-Host "Shop subdomain"
$clientId = Read-Host "Shopify client ID"
$secureSecret = Read-Host "Shopify client secret" -AsSecureString
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
try {
    $clientSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    & java "-DSHOPIFY_SHOP=$shopName" "-DSHOPIFY_CLIENT_ID=$clientId" "-DSHOPIFY_CLIENT_SECRET=$clientSecret" "-DSHOPIFY_API_VERSION=2026-07" -jar (Join-Path $projectDir "backend/target/shopify-dashboard.jar")
    if ($LASTEXITCODE -ne 0) { throw "Application exited with an error" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    $clientSecret = $null
}
