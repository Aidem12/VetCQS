$ErrorActionPreference = 'Stop'
$repoVet = Split-Path $PSScriptRoot -Parent
Set-Location $repoVet
docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Inicie Docker Desktop antes de continuar.' }
docker compose up -d bd
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL.' }
$readyVet = $false
for ($i = 0; $i -lt 30; $i++) {
    docker compose exec -T bd pg_isready -U veterinaria -d veterinaria *> $null
    if ($LASTEXITCODE -eq 0) { $readyVet = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $readyVet) { throw 'PostgreSQL no responde. No se modificó el servidor.' }
$backupVet = Join-Path $repoVet ('respaldos/veterinaria-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
New-Item -ItemType Directory -Force (Split-Path $backupVet -Parent) | Out-Null
$dumpVet = docker compose exec -T bd pg_dump -U veterinaria -d veterinaria --no-owner --no-privileges
if ($LASTEXITCODE -ne 0) { throw 'No se pudo respaldar la base. Se cancela la actualización.' }
[IO.File]::WriteAllLines($backupVet, [string[]]$dumpVet, [Text.UTF8Encoding]::new($false))
Write-Host "Respaldo guardado: $backupVet"
docker compose up -d --build --no-deps servidor
if ($LASTEXITCODE -ne 0) { throw 'No se pudo reconstruir el servidor. Revise el mensaje de Docker.' }
for ($i = 0; $i -lt 45; $i++) {
    try {
        $healthVet = Invoke-RestMethod http://localhost:8080/api/salud -TimeoutSec 3
        if ($healthVet.versionApi -eq 'estabilizacion-1') {
            Write-Host 'Servidor actualizado y conectado a PostgreSQL. Cierre las ventanas de VetCQS y abra el portal de nuevo.' -ForegroundColor Green
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 2
}
throw 'No se confirmó la versión nueva. Consulte docker compose logs --tail 80 servidor.'
