$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    throw ".env was not found in project root: $projectRoot"
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
        return
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1)
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

# For the first live run we want real portal data, not demo seed data.
$env:APP_DEMO_ENABLED = "false"
$env:TAX_PORTAL_BROWSER_ENABLED = "true"
$env:TAX_PORTAL_HEADLESS = "true"
$postgresPort = if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PORT)) { "5432" } else { $env:POSTGRES_PORT }
$env:DEV_DB_URL = "jdbc:postgresql://localhost:$postgresPort/gaime_bridge"
$env:DEV_DB_USERNAME = if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "gaime_bridge" } else { $env:DB_USERNAME }
$env:DEV_DB_PASSWORD = if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { "gaime_bridge" } else { $env:DB_PASSWORD }
$env:EXPORT_BASE_DIR = "./exports/live"
$env:TAX_PORTAL_STORAGE_STATE_PATH = ".run/playwright-tax-portal-state.json"

$required = @(
    "TAX_PORTAL_LOGIN_URL",
    "TAX_PORTAL_USERNAME_SELECTOR",
    "TAX_PORTAL_PASSWORD_SELECTOR",
    "TAX_PORTAL_SUBMIT_SELECTOR",
    "TAX_PORTAL_VERIFICATION_URL",
    "TAX_PORTAL_VERIFICATION_START_PATH",
    "TAX_PORTAL_VERIFICATION_STATUS_PATH",
    "TAX_PORTAL_CERTIFICATES_PATH",
    "TAX_PORTAL_CHOOSE_TAXPAYER_PATH",
    "TAX_PORTAL_HOME_URL"
)

$missing = @()
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value)) {
        $missing += $name
    }
}

if ($missing.Count -gt 0) {
    throw "Fill these .env values before live run: $($missing -join ', ')"
}

Write-Host "Starting live Asan run with dev profile and demo data disabled..." -ForegroundColor Cyan
Write-Host "Browser automation is enabled and headless mode is ON." -ForegroundColor Cyan
Write-Host "Saved portal session state: $env:TAX_PORTAL_STORAGE_STATE_PATH" -ForegroundColor Cyan
Write-Host "Using local PostgreSQL database for live sync: $env:DEV_DB_URL" -ForegroundColor Cyan
Write-Host "Using isolated live exports directory: ./exports/live" -ForegroundColor Cyan

Push-Location $projectRoot
try {
    ./gradlew.bat bootRun --args="--spring.profiles.active=dev"
}
finally {
    Pop-Location
}
