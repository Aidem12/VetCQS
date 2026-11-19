@echo off
title VetCQS - semana 6
cd /d "%~dp0"
docker info >nul 2>&1 && goto dockerok
echo Abriendo Docker Desktop...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
:esperadocker
timeout /t 5 /nobreak >nul
docker info >nul 2>&1 || goto esperadocker
:dockerok
echo Levantando servidor y base de datos (la primera vez tarda varios minutos)...
docker rm -f vet-servidor vet-bd >nul 2>&1
if not exist .env copy .env.ejemplo .env >nul
docker compose up -d --build
if errorlevel 1 goto error
echo Esperando a que el servidor responda...
:esperaservidor
timeout /t 3 /nobreak >nul
curl -s http://localhost:8080/api/salud | find "OK" >nul || goto esperaservidor
echo Servidor listo. Abriendo el programa...
cd cliente-portal
call mvn -q javafx:run
pause
exit /b
:error
echo Hubo un error al levantar el servidor con Docker.
pause
