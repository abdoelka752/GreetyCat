param([ValidateRange(1, 65535)][int]$Port = 8765)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonPath -PathType Leaf)) {
    throw 'Project Python environment is missing. Follow backend/README.md setup first.'
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    & $pythonPath -m greedycat.server --port $Port
    if ($LASTEXITCODE -ne 0) { throw "Local server exited with code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
