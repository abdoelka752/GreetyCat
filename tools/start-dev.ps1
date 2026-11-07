param([ValidateRange(1, 65535)][int]$Port = 8765)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $projectRoot 'web'
if (-not (Test-Path -LiteralPath (Join-Path $webRoot 'node_modules') -PathType Container)) {
    throw 'Dashboard dependencies are missing. Run npm.cmd ci from web/ first.'
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.venv\Scripts\python.exe') -PathType Leaf)) {
    throw 'Project Python environment is missing. Follow web/README.md setup first.'
}
Push-Location $webRoot
try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw "Dashboard build failed with code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
Write-Host "Dashboard: http://127.0.0.1:$Port - stop with Ctrl+C"
& (Join-Path $PSScriptRoot 'start-server.ps1') -Port $Port
