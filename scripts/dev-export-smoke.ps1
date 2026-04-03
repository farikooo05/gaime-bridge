$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$username = "dev"
$password = "dev123"

$pair = "{0}:{1}" -f $username, $password
$encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{
    Authorization = "Basic $encoded"
}

Write-Host "Checking health..."
$health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
if ($health.status -ne "UP") {
    throw "Application is not healthy"
}

Write-Host "Loading demo documents..."
$documents = Invoke-RestMethod -Uri "$baseUrl/api/v1/documents" -Headers $headers -Method Get
$documentItems = @()
if ($documents.items) {
    $documentItems = @($documents.items)
} elseif ($documents.content) {
    $documentItems = @($documents.content)
}

if (-not $documentItems -or $documentItems.Count -eq 0) {
    throw "No demo documents were returned"
}

$firstDocument = $documentItems[0]
Write-Host ("Using document: {0} ({1})" -f $firstDocument.documentNumber, $firstDocument.id)

$exportRequest = @{
    format = "JSON"
    documentIds = @($firstDocument.id)
} | ConvertTo-Json -Depth 5

Write-Host "Creating export..."
$exportJob = Invoke-RestMethod -Uri "$baseUrl/api/v1/exports" -Headers $headers -Method Post -ContentType "application/json" -Body $exportRequest
if ($exportJob.status -ne "COMPLETED") {
    throw ("Export job failed with status: {0}" -f $exportJob.status)
}

$targetDir = Join-Path (Resolve-Path ".") "exports\dev\manual"
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
$targetFile = Join-Path $targetDir ("export-{0}.json" -f $exportJob.id)

Write-Host "Downloading export file..."
Invoke-WebRequest -Uri "$baseUrl/api/v1/exports/$($exportJob.id)/file" -Headers $headers -OutFile $targetFile

Write-Host ""
Write-Host "Smoke flow completed successfully."
Write-Host ("Document ID: {0}" -f $firstDocument.id)
Write-Host ("Export job ID: {0}" -f $exportJob.id)
Write-Host ("Saved file: {0}" -f $targetFile)
