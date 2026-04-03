$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$baseUrl = "http://localhost:8080"
$runDir = Join-Path $projectRoot ".run"
$stdoutLog = Join-Path $runDir "dev-boot.out.log"
$stderrLog = Join-Path $runDir "dev-boot.err.log"
$pidFile = Join-Path $runDir "dev-boot.pid"
$envFile = Join-Path $projectRoot ".env"

function Resolve-JavaHome {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME) -and (Test-SupportedJavaHome -JavaHome $env:JAVA_HOME)) {
        return $env:JAVA_HOME
    }

    $candidates = @(
        "C:\Program Files\Java\jdk-21"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "JAVA_HOME must point to a JDK 21 installation for bootRun in this repository. Install JDK 21 or set JAVA_HOME to it first."
}

function Test-SupportedJavaHome {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JavaHome
    )

    if (-not (Test-Path $JavaHome)) {
        return $false
    }

    $releaseFile = Join-Path $JavaHome "release"
    if (-not (Test-Path $releaseFile)) {
        return $false
    }

    try {
        $releaseContent = Get-Content $releaseFile -Raw
        return $releaseContent -match 'JAVA_VERSION="21(\.|")'
    }
    catch {
        return $false
    }
}

function Test-DevAppHealthy {
    try {
        $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get -TimeoutSec 5
        return $health.status -eq "UP"
    } catch {
        return $false
    }
}

function Import-DotEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string] $EnvFile
    )

    if (-not (Test-Path $EnvFile)) {
        return
    }

    Get-Content $EnvFile | ForEach-Object {
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
}

function Test-PostgresListening {
    param(
        [Parameter(Mandatory = $true)]
        [int] $Port
    )

    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1500)) {
            $client.Close()
            return $false
        }
        $client.EndConnect($async)
        $client.Close()
        return $true
    }
    catch {
        return $false
    }
}

function Wait-ForDevApp {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process] $Process,
        [int] $TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "The dev app stopped before becoming healthy. Check $stdoutLog and $stderrLog."
        }

        if (Test-DevAppHealthy) {
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for the dev app to become healthy. Check $stdoutLog and $stderrLog."
}

Push-Location $projectRoot
try {
    Import-DotEnv -EnvFile $envFile
    $javaHome = Resolve-JavaHome
    $env:JAVA_HOME = $javaHome
    $env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path
    $postgresPort = if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PORT)) { 5432 } else { [int]$env:POSTGRES_PORT }
    $env:DEV_DB_URL = "jdbc:postgresql://localhost:$postgresPort/gaime_bridge"
    $env:DEV_DB_USERNAME = if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "gaime_bridge" } else { $env:DB_USERNAME }
    $env:DEV_DB_PASSWORD = if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { "gaime_bridge" } else { $env:DB_PASSWORD }

    if (-not (Test-PostgresListening -Port $postgresPort)) {
        throw "Local PostgreSQL is not reachable on port $postgresPort. Start your PostgreSQL service or run 'docker compose up -d postgres' first."
    }

    $startedProcess = $null
    if (Test-DevAppHealthy) {
        Write-Host "Reusing existing healthy dev app on $baseUrl" -ForegroundColor Cyan
    } else {
        New-Item -ItemType Directory -Force $runDir | Out-Null
        if (Test-Path $stdoutLog) {
            Remove-Item -LiteralPath $stdoutLog -Force
        }
        if (Test-Path $stderrLog) {
            Remove-Item -LiteralPath $stderrLog -Force
        }

        Write-Host "Starting app with dev profile against local PostgreSQL..." -ForegroundColor Cyan
        $javaBinPath = Join-Path $javaHome "bin"
        $launchCommand = @"
`$env:JAVA_HOME = '$javaHome'
`$env:Path = '$javaBinPath;' + `$env:Path
New-Item -ItemType Directory -Force '.gradle-user-home' | Out-Null
`$env:GRADLE_USER_HOME = (Resolve-Path '.gradle-user-home')
`$env:DEV_DB_URL = '$($env:DEV_DB_URL)'
`$env:DEV_DB_USERNAME = '$($env:DEV_DB_USERNAME)'
`$env:DEV_DB_PASSWORD = '$($env:DEV_DB_PASSWORD)'
./gradlew.bat bootRun --args="--spring.profiles.active=dev"
"@
        $encodedLaunchCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($launchCommand))

        $quotedStdoutLog = '"' + $stdoutLog + '"'
        $quotedStderrLog = '"' + $stderrLog + '"'

        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = "cmd.exe"
        $startInfo.WorkingDirectory = $projectRoot
        $startInfo.UseShellExecute = $false
        $startInfo.Arguments = "/c powershell.exe -NoProfile -EncodedCommand $encodedLaunchCommand 1> $quotedStdoutLog 2> $quotedStderrLog"
        $startedProcess = [System.Diagnostics.Process]::new()
        $startedProcess.StartInfo = $startInfo
        $null = $startedProcess.Start()

        Set-Content -Path $pidFile -Value $startedProcess.Id
        Wait-ForDevApp -Process $startedProcess
    }

    & (Join-Path $PSScriptRoot "dev-export-smoke.ps1")

    if ($startedProcess -ne $null) {
        Write-Host ""
        Write-Host "App is still running for local debugging." -ForegroundColor Green
        Write-Host ("PID file: {0}" -f $pidFile)
        Write-Host ("Stdout log: {0}" -f $stdoutLog)
        Write-Host ("Stderr log: {0}" -f $stderrLog)
    }
}
finally {
    Pop-Location
}
