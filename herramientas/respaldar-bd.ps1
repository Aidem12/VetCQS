# Respaldo de la base de datos de VetCQS (PostgreSQL en Docker).
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas\respaldar-bd.ps1
# Genera respaldos\veterinaria-AAAAMMDD-HHMMSS.dump (formato personalizado de pg_dump, comprimido)
# y un archivo .sha256 para verificar que no se dañó. No detiene ni modifica la base.
param([int]$ConservarDias = 30)
$ErrorActionPreference = 'Stop'
$repoVet = Split-Path $PSScriptRoot -Parent
Set-Location $repoVet
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Inicie Docker Desktop antes de respaldar.' }
docker compose exec -T bd pg_isready -U veterinaria -d veterinaria *> $null
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL (vet-bd) no está en ejecución. Inícielo con: docker compose up -d bd' }
$carpeta = Join-Path $repoVet 'respaldos'
New-Item -ItemType Directory -Force $carpeta | Out-Null
$nombre = 'veterinaria-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump'
$destino = Join-Path $carpeta $nombre
# El volcado se genera dentro del contenedor y se copia: así no se alteran bytes binarios en la consola.
docker compose exec -T bd pg_dump -U veterinaria -d veterinaria --format=custom --no-owner --no-privileges --file=/tmp/$nombre
if ($LASTEXITCODE -ne 0) { throw 'pg_dump falló. No se generó respaldo.' }
docker compose cp "bd:/tmp/$nombre" $destino
if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el respaldo fuera del contenedor.' }
docker compose exec -T bd rm -f "/tmp/$nombre" | Out-Null
$hash = (Get-FileHash $destino -Algorithm SHA256).Hash.ToLower()
"$hash  $nombre" | Set-Content -Encoding ascii ($destino + '.sha256')
# Verificación: pg_restore debe poder leer el índice del respaldo.
docker compose cp $destino "bd:/tmp/verificar.dump" | Out-Null
docker compose exec -T bd pg_restore --list /tmp/verificar.dump *> $null
$ok = $LASTEXITCODE -eq 0
docker compose exec -T bd rm -f /tmp/verificar.dump | Out-Null
if (-not $ok) { throw "El respaldo $nombre no se pudo leer con pg_restore. Revise el espacio en disco y repita." }
Write-Host "Respaldo verificado: $destino" -ForegroundColor Green
Write-Host "SHA-256: $hash"
if ($ConservarDias -gt 0) {
    Get-ChildItem $carpeta -Filter 'veterinaria-*.dump' | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$ConservarDias) } | ForEach-Object {
        Write-Host "Respaldo antiguo conservado (bórrelo manualmente si ya no lo necesita): $($_.Name)"
    }
}
