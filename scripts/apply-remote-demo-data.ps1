param(
    [string]$DbHost = "127.0.0.1",
    [int]$Port = 3307,
    [string]$Database = "courtflow",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$scriptPath = Join-Path $PSScriptRoot "seed-remote-demo-data.sql"

if (-not (Test-Path $mysql)) {
    throw "mysql.exe not found at $mysql"
}

if (-not (Test-Path $scriptPath)) {
    throw "SQL script not found at $scriptPath"
}

$env:MYSQL_PWD = $Password
try {
    Get-Content -Raw $scriptPath | & $mysql -h $DbHost -P $Port -u $Username -D $Database --default-character-set=utf8mb4
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
