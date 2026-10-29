# PowerShell script to build and launch the Smart Home Docker Compose stack
# ---------------------------------------------------------------
# Prerequisite: Docker Desktop must be installed and running.
# ---------------------------------------------------------------

# 1️⃣ Add Docker binaries (including credential helper) to PATH
$dockerBin = "C:\Users\mruth_cf5z57d\AppData\Local\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) {
    $env:Path += ";$dockerBin"
    Write-Host "[INFO] Added Docker binaries to PATH." -ForegroundColor Cyan
} else {
    Write-Host "[ERROR] Docker binaries not found at $dockerBin. Install Docker Desktop." -ForegroundColor Red
    exit 1
}

# Ensure the credential helper is reachable
if (-not (Get-Command docker-credential-desktop -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] docker-credential-desktop not found in PATH. Check Docker Desktop installation." -ForegroundColor Red
    exit 1
}

# 2️⃣ Hide the old compose.yaml so Docker uses docker-compose.yml
$oldCompose = Join-Path $PSScriptRoot "compose.yaml"
if (Test-Path $oldCompose) {
    Rename-Item -Path $oldCompose -NewName "compose.yaml.bak" -Force
    Write-Host "[INFO] Renamed compose.yaml → compose.yaml.bak" -ForegroundColor Cyan
}

# 3️⃣ Run Docker Compose (build & start in detached mode)
Write-Host "\n[INFO] Building and starting containers..." -ForegroundColor Cyan
docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] docker compose up failed. See Docker Desktop dashboard for details." -ForegroundColor Red
    exit $LASTEXITCODE
}

# 4️⃣ Simple health‑check loop (max 30 seconds)
$port = $env:CONTROLLER_PORT
if (-not $port) { $port = 5000 }
$maxAttempts = 15
$attempt = 0
while ($attempt -lt $maxAttempts) {
    Start-Sleep -Seconds 2
    $attempt++
    try {
        $status = Invoke-RestMethod -Uri "http://localhost:$port/api/status" -Method Get -TimeoutSec 5
        Write-Host "\n[SUCCESS] Controller is up – telemetry:" -ForegroundColor Green
        $status | ConvertTo-Json -Depth 4
        break
    } catch {
        Write-Host "[INFO] Waiting for controller… ($attempt/$maxAttempts)" -ForegroundColor Yellow
    }
}
if ($attempt -eq $maxAttempts) {
    Write-Host "\n[ERROR] Controller did not become healthy within the timeout." -ForegroundColor Red
    Write-Host "Check logs with: docker logs smarthome-controller" -ForegroundColor Yellow
    exit 1
}

Write-Host "\n[INFO] All set! Open the dashboard at:" -ForegroundColor Cyan
Write-Host "    http://localhost:$port" -ForegroundColor Cyan
Write-Host "To stop the stack later run:" -ForegroundColor Cyan
Write-Host "    docker compose down" -ForegroundColor Cyan
