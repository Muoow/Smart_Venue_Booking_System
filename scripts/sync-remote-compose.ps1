param(
    [Parameter(Mandatory = $true)]
    [string]$ServerHost,

    [string]$ServerUser = "ubuntu",

    [Parameter(Mandatory = $true)]
    [string]$KeyPath,

    [string]$RemoteDir = "/home/ubuntu/courtflow",
    [string]$TemplatePath = ""
)

$ErrorActionPreference = "Stop"
$resolvedKeyPath = (Resolve-Path $KeyPath).Path

if ([string]::IsNullOrWhiteSpace($TemplatePath)) {
    $TemplatePath = (Resolve-Path (Join-Path $PSScriptRoot "..\deploy\remote\docker-compose.remote.yml")).Path
} else {
    $TemplatePath = (Resolve-Path $TemplatePath).Path
}

function Invoke-Ssh {
    param([string]$Command)

    & ssh.exe -i $resolvedKeyPath -o BatchMode=yes -o StrictHostKeyChecking=no "$ServerUser@$ServerHost" $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed: $Command"
    }
}

Write-Host "Backing up remote docker-compose.yml..."
Invoke-Ssh "if [ -f '$RemoteDir/docker-compose.yml' ]; then cp '$RemoteDir/docker-compose.yml' '$RemoteDir/docker-compose.yml.bak.`$(date +%Y%m%d%H%M%S)'; fi"

Write-Host "Uploading compose template..."
& scp.exe -i $resolvedKeyPath -o BatchMode=yes -o StrictHostKeyChecking=no $TemplatePath "${ServerUser}@${ServerHost}:$RemoteDir/docker-compose.yml"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to upload docker-compose template."
}

Write-Host "Compose file synced to ${ServerUser}@${ServerHost}:${RemoteDir}/docker-compose.yml"
