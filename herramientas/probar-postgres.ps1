param([string]$RepositorioMaven = "$env:USERPROFILE\.m2\repository")
$ErrorActionPreference = 'Stop'
$repoVet = Split-Path $PSScriptRoot -Parent
$configVet = @{}
Get-Content (Join-Path $repoVet '.env') -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $configVet[$matches[1].Trim()] = $matches[2].Trim() }
}
if (-not $configVet['BD_PASSWORD']) { throw 'Falta BD_PASSWORD en .env.' }
$env:VET_POSTGRES_TEST = 'true'
$env:VET_TEST_DB_PASSWORD = $configVet['BD_PASSWORD']
$agentVet = Get-ChildItem (Join-Path $RepositorioMaven 'net/bytebuddy/byte-buddy-agent') -Recurse -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
try {
    $optionsVet = @('-f', (Join-Path $repoVet 'servidor/pom.xml'), 'test')
    if ($agentVet) { $optionsVet += "-DargLine=-javaagent:$($agentVet.FullName)" }
    & mvn @optionsVet
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas. Revise servidor/target/surefire-reports.' }
} finally {
    Remove-Item Env:VET_POSTGRES_TEST -ErrorAction SilentlyContinue
    Remove-Item Env:VET_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
}
