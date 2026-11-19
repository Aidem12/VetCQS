# Restauración de un respaldo de VetCQS.
# Por seguridad, por defecto restaura en una base NUEVA e independiente (veterinaria_restaurada) para revisarla
# sin tocar la base en uso. Solo con -SobrescribirBaseOperativa reemplaza la base real, y antes hace un
# respaldo de seguridad de su estado actual.
# Uso:
#   powershell -ExecutionPolicy Bypass -File herramientas\restaurar-bd.ps1 -Respaldo respaldos\veterinaria-20261118-090000.dump
#   (prueba en base aparte; luego revise con: docker compose exec bd psql -U veterinaria -d veterinaria_restaurada)
param(
    [Parameter(Mandatory = $true)][string]$Respaldo,
    [string]$BaseDestino = 'veterinaria_restaurada',
    [switch]$SobrescribirBaseOperativa
)
$ErrorActionPreference = 'Stop'
$repoVet = Split-Path $PSScriptRoot -Parent
Set-Location $repoVet
if (-not (Test-Path $Respaldo)) { throw "No existe el archivo $Respaldo" }
if ($BaseDestino -notmatch '^[a-z_][a-z0-9_]{0,40}$') { throw 'Nombre de base inválido.' }
$sha = $Respaldo + '.sha256'
if (Test-Path $sha) {
    $esperado = ((Get-Content $sha -Raw).Trim() -split '\s+')[0]
    $actual = (Get-FileHash $Respaldo -Algorithm SHA256).Hash.ToLower()
    if ($esperado -ne $actual) { throw 'El SHA-256 del respaldo no coincide: el archivo está dañado o fue modificado.' }
    Write-Host 'Integridad SHA-256 verificada.'
}
docker compose exec -T bd pg_isready -U veterinaria -d veterinaria *> $null
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL (vet-bd) no está en ejecución.' }
if ($SobrescribirBaseOperativa) {
    if ($BaseDestino -ne 'veterinaria_restaurada') { throw 'Use -SobrescribirBaseOperativa sin -BaseDestino.' }
    $respuesta = Read-Host 'Esto REEMPLAZA la base operativa "veterinaria". Escriba RESTAURAR para continuar'
    if ($respuesta -ne 'RESTAURAR') { Write-Host 'Cancelado. No se modificó nada.'; exit 1 }
    & (Join-Path $PSScriptRoot 'respaldar-bd.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo respaldar el estado actual; se cancela la restauración.' }
    docker compose stop servidor | Out-Null
    $BaseDestino = 'veterinaria'
}
docker compose cp $Respaldo 'bd:/tmp/restaurar.dump'
if ($BaseDestino -ne 'veterinaria') {
    docker compose exec -T bd psql -U veterinaria -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $BaseDestino" -c "CREATE DATABASE $BaseDestino OWNER veterinaria"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base de prueba.' }
    docker compose exec -T bd pg_restore -U veterinaria -d $BaseDestino --no-owner --no-privileges --exit-on-error /tmp/restaurar.dump
} else {
    docker compose exec -T bd pg_restore -U veterinaria -d veterinaria --clean --if-exists --no-owner --no-privileges --exit-on-error /tmp/restaurar.dump
}
$codigo = $LASTEXITCODE
docker compose exec -T bd rm -f /tmp/restaurar.dump | Out-Null
if ($SobrescribirBaseOperativa) { docker compose start servidor | Out-Null }
if ($codigo -ne 0) { throw 'pg_restore reportó errores. Revise el mensaje anterior.' }
$conteo = docker compose exec -T bd psql -U veterinaria -d $BaseDestino -tAc "select (select count(*) from usuario)||' usuarios, '||(select count(*) from venta)||' ventas, '||(select count(*) from mascota)||' mascotas'"
Write-Host "Restauración correcta en '$BaseDestino': $conteo" -ForegroundColor Green
