# Runs the full test suite, including the real PostgreSQL tests.
# Reads the connection from weather/.env (never printed) and clears it afterwards.
# Usage, from the weather folder:  .\scripts\test-all.ps1
$ErrorActionPreference = 'Stop'
$weather = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $weather '.env'
if (-not (Test-Path $envFile)) { throw "Missing $envFile" }

$map = @{
    URL      = 'GLEAWEATHER_TEST_JDBC_URL'
    USERNAME = 'GLEAWEATHER_TEST_DB_USER'
    PASSWORD = 'GLEAWEATHER_TEST_DB_PASSWORD'
}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^SPRING_DATASOURCE_(URL|USERNAME|PASSWORD)=(.*)$') {
        Set-Item "env:$($map[$Matches[1]])" $Matches[2].Trim()
    }
}
foreach ($name in $map.Values) {
    if (-not (Test-Path "env:$name")) { throw "$name not found in .env" }
}

Push-Location $weather
try {
    & .\mvnw.cmd "-Dstations.postgres.test=true" "-Dacquisition.runs.postgres.test=true" test @args
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $map.Values) { Remove-Item "env:$name" -ErrorAction SilentlyContinue }
}
exit $code
