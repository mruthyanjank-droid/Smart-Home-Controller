# PowerShell script to build and run the Smart Home Docker Compose stack
# ---------------------------------------------------------------
# Usage:
#   .\run.ps1          # Build + start stack (detached)
#   .\run.ps1 -down    # Stop and remove containers
#   .\run.ps1 -rebuild # Force rebuild before start
#   .\run.ps1 -port 6000 # Override CONTROLLER_PORT environment variable
#
# Prerequisites:
#   • Docker Desktop installed and docker.exe on PATH
#   • PowerShell 5+ (default on Windows 10/11)
# ---------------------------------------------------------------
param(
    [switch]$down,
    [switch]$rebuild,
    [int]$port
)

function Write-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-ErrorMsg($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# Ensure docker is available
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-ErrorMsg "Docker CLI not found. Install Docker Desktop and ensure \"docker\" is on PATH."
    exit 1
}

# Optional shutdown
if ($down) {
    Write-Info "Stopping existing Docker Compose stack…"
    docker compose down
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorMsg "docker compose down failed."
        exit $LASTEXITCODE
    }
    Write-Info "Stack stopped. Exiting."
    exit 0
}

# Set optional port env var
if ($port) {
    $env:CONTROLLER_PORT = $port
    Write-Info "Overriding CONTROLLER_PORT to $port"
}

# Build and start stack
$composeCmd = "docker compose up --build -d"
if ($rebuild) {
    $composeCmd = "docker compose up --build --force-recreate -d"
}
Write-Info "Running: $composeCmd"
& cmd /c $composeCmd
if ($LASTEXITCODE -ne 0) {
    Write-ErrorMsg "docker compose up failed."
    exit $LASTEXITCODE
}

# Simple health‑check loop (wait up to 30 s)
$maxAttempts = 15
$attempt = 0
while ($attempt -lt $maxAttempts) {
    Start-Sleep -Seconds 2
    $attempt++
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:${env:CONTROLLER_PORT:-5000}/api/status" -Method Get -TimeoutSec 5
        Write-Info "Controller is up – status: $(ConvertTo-Json $resp -Depth 3)"
        break
    } catch {
        Write-Info "Waiting for controller… (attempt $attempt/$maxAttempts)"
    }
}
if ($attempt -eq $maxAttempts) {
    Write-ErrorMsg "Controller did not become healthy within the timeout. Check logs with 'docker logs smarthome-controller'."
}

Write-Info "Done. Open http://localhost:${env:CONTROLLER_PORT:-5000} in a browser to access the dashboard."
