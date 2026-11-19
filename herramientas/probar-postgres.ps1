param([string]$RepositorioMaven = "$env:USERPROFILE\.m2\repository")
$ErrorActionPreference = 'Stop'
$repoVet = Split-Path $PSScriptRoot -Parent
if ($env:VET_TEST_DB_URL -notmatch '^jdbc:postgresql://(localhost|127\.0\.0\.1):[0-9]+/vetcqs_test$' -or -not $env:VET_TEST_DB_PASSWORD) {
    throw 'Configure VET_TEST_DB_URL y VET_TEST_DB_PASSWORD de una base independiente vetcqs_test. No use la base real.'
}
$env:VET_POSTGRES_TEST = 'true'
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
