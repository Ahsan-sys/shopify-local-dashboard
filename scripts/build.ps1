$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $projectDir "frontend")
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
    npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed" }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed" }
} finally { Pop-Location }
Push-Location (Join-Path $projectDir "backend")
try {
    .\mvnw.cmd clean verify
    if ($LASTEXITCODE -ne 0) { throw "Backend build or tests failed" }
} finally { Pop-Location }
Write-Host "Built backend/target/shopify-dashboard.jar"
