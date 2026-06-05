param(
    [Parameter(Mandatory = $true)]
    [string]$ServerHost,

    [string]$ServerUser = "ubuntu",

    [Parameter(Mandatory = $true)]
    [string]$KeyPath,

    [string]$RemoteDir = "/home/ubuntu/courtflow",
    [string]$DbContainer = "mysql",
    [string]$DbName = "courtflow",
    [string]$DbUser = "admin",
    [string]$DbPassword = "admin123",
    [string]$AppContainer = "courtflow",
    [string]$HealthUrl = "http://127.0.0.1:8080/actuator/health",
    [switch]$RestartApp,
    [switch]$SkipSchema,
    [switch]$SkipSeed,
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Stop"

$schemaPath = (Resolve-Path (Join-Path $PSScriptRoot "..\deploy\mysql\init\01_schema.sql")).Path
$seedPath = (Resolve-Path (Join-Path $PSScriptRoot "..\deploy\mysql\init\02_seed.sql")).Path
$resolvedKeyPath = (Resolve-Path $KeyPath).Path
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$remoteTempDir = "$RemoteDir/.deploy-$timestamp"

function Invoke-Ssh {
    param([string]$Command)

    & ssh.exe -i $resolvedKeyPath -o BatchMode=yes -o StrictHostKeyChecking=no "$ServerUser@$ServerHost" $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed: $Command"
    }
}

function Invoke-Scp {
    param(
        [string]$LocalPath,
        [string]$RemotePath
    )

    & scp.exe -i $resolvedKeyPath -o BatchMode=yes -o StrictHostKeyChecking=no $LocalPath "${ServerUser}@${ServerHost}:$RemotePath"
    if ($LASTEXITCODE -ne 0) {
        throw "File upload failed: $LocalPath -> $RemotePath"
    }
}

Write-Host "Preparing remote temp directory: $remoteTempDir"
Invoke-Ssh "mkdir -p '$remoteTempDir'"

if (-not $SkipSchema) {
    Write-Host "Uploading schema script..."
    Invoke-Scp $schemaPath "$remoteTempDir/01_schema.sql"
}

if (-not $SkipSeed) {
    Write-Host "Uploading seed script..."
    Invoke-Scp $seedPath "$remoteTempDir/02_seed.sql"
}

if (-not $SkipSchema) {
    Write-Host "Applying schema..."
    Invoke-Ssh "sudo docker exec -i $DbContainer mysql -u$DbUser -p$DbPassword $DbName < '$remoteTempDir/01_schema.sql'"
}

if (-not $SkipSeed) {
    Write-Host "Applying seed data..."
    Invoke-Ssh "sudo docker exec -i $DbContainer mysql -u$DbUser -p$DbPassword $DbName < '$remoteTempDir/02_seed.sql'"
}

if ($RestartApp) {
    Write-Host "Restarting app container: $AppContainer"
    Invoke-Ssh "sudo docker restart $AppContainer"
}

if (-not $SkipHealthCheck) {
    Write-Host "Checking health endpoint..."
    $healthOk = $false
    for ($i = 0; $i -lt 20; $i++) {
        try {
            Invoke-Ssh "curl -fsS '$HealthUrl' >/dev/null"
            $healthOk = $true
            break
        } catch {
            Start-Sleep -Seconds 3
        }
    }

    if (-not $healthOk) {
        throw "Health check failed: $HealthUrl"
    }
}

Write-Host "Cleaning remote temp files..."
Invoke-Ssh "rm -rf '$remoteTempDir'"

Write-Host ""
Write-Host "Remote bootstrap completed successfully."
Write-Host "Server: $ServerUser@$ServerHost"
Write-Host "Remote dir: $RemoteDir"
Write-Host "Schema applied: $(-not $SkipSchema)"
Write-Host "Seed applied: $(-not $SkipSeed)"
Write-Host "App restarted: $RestartApp"
