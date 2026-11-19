# Operación de VetCQS en Windows

Guía para instalar, actualizar, respaldar y recuperar VetCQS en una computadora con Windows 10/11.

## Requisitos
- Docker Desktop (servidor y PostgreSQL).
- Java 21 (Temurin) y Maven 3.9 o superior, para compilar y abrir los clientes JavaFX.
- Git.

## Configuración (archivo `.env`, privado)
Copie `.env.ejemplo` a `.env` y cambie las contraseñas. **No suba `.env` a Git** (ya está en `.gitignore`).
Variables opcionales nuevas (todas tienen valor predeterminado):

| Variable | Uso | Predeterminado |
|---|---|---|
| `NEGOCIO_NOMBRE`, `NEGOCIO_DIRECCION`, `NEGOCIO_TELEFONO`, `NEGOCIO_RFC`, `NEGOCIO_LEYENDA` | Datos del ticket, recetas y reportes | Clínica Veterinaria |
| `SESSION_TIMEOUT` | Minutos de inactividad antes de cerrar sesión (ej. `60m`) | 60m |
| `COOKIE_SECURE` | `true` solo si el servidor se publica con HTTPS | false |
| `APP_ALLOWED_ORIGINS` | Orígenes permitidos para WebSocket | http://localhost:8080 |
| `FLYWAY_ENABLED` | Migraciones automáticas al arrancar | true |
| `FACTURACION_PROVEEDOR` | `demostracion` (único disponible; no timbra ante el SAT) | demostracion |

Las de negocio y `SESSION_TIMEOUT` ya están conectadas en `docker-compose.yml`: basta con escribirlas en `.env`.

## Primer arranque
```powershell
cd C:\ruta\VetCQS
copy .env.ejemplo .env      # y edite las contraseñas
docker compose up -d --build
```
La primera vez PostgreSQL ejecuta `bd/01..03`. Al arrancar, el servidor aplica las migraciones Flyway (`servidor/src/main/resources/db/migration`) y no borra datos.

Comprobación: abra `http://localhost:8080/api/salud`; debe responder `"estado":"OK"` y `"versionApi":"integral-1"`.

## Abrir el sistema
Doble clic en `iniciar-vetcqs.bat` (o `cd cliente-portal` y `mvn javafx:run`). El portal abre Caja y Consultorio según el rol. La sesión pasa a esas ventanas por una variable de entorno, no por la línea de comandos.

Atajos de teclado: Caja F1 precuenta · F2 buscar · F4 inventario · F5 cobrar · F6 devolución. Consultorio F2 buscar · F3 agenda · F4 inventario · F10 enviar precuenta · Ctrl+S guardar.

## Respaldo
```powershell
powershell -ExecutionPolicy Bypass -File herramientas\respaldar-bd.ps1
```
Genera `respaldos\veterinaria-AAAAMMDD-HHMMSS.dump` y un `.sha256`, y comprueba que `pg_restore` puede leer el archivo. Solo lee la base: no la detiene ni la modifica. Se recomienda un respaldo diario y copiar la carpeta `respaldos` a otra unidad.

## Restauración
1. **Prueba (recomendada):** restaura en una base aparte, `veterinaria_restaurada`, sin tocar la base en uso:
   ```powershell
   powershell -ExecutionPolicy Bypass -File herramientas\restaurar-bd.ps1 -Respaldo respaldos\veterinaria-20261118-090000.dump
   ```
2. **Reemplazar la base real** (solo en caso de desastre): agregue `-SobrescribirBaseOperativa`. El script pide escribir `RESTAURAR`, hace un respaldo del estado actual, detiene el servidor, restaura y lo vuelve a iniciar.

## Actualizar el servidor
```powershell
powershell -ExecutionPolicy Bypass -File herramientas\actualizar-servidor.ps1
```
El script respalda la base, reconstruye solo el contenedor `servidor` (no borra el volumen `datos-bd`) y espera `versionApi = integral-1`. Después, cierre todas las ventanas de VetCQS y vuelva a abrir el portal.

Nunca use `docker compose down -v`: borra el volumen con todos los datos.

## Si una migración falla
1. El servidor no arranca y `docker compose logs --tail 80 servidor` muestra el error de Flyway. La base queda tal como estaba antes de la migración fallida, porque cada migración corre en una transacción de PostgreSQL.
2. No edite a mano `flyway_schema_history`. Corrija el script o vuelva a la versión anterior del servidor (`git checkout <versión>` y `docker compose up -d --build --no-deps servidor`).
3. Si hubo daño en los datos, restaure el respaldo que hizo `actualizar-servidor.ps1` (paso "Restauración").

## Entornos separados
- **Operación:** contenedores `vet-bd` / `vet-servidor` y volumen `datos-bd`.
- **Pruebas:** contenedor independiente con la base `vetcqs_test` (por ejemplo, `vetcqs-pg-pruebas`) y las variables `VET_TEST_DB_URL` y `VET_TEST_DB_PASSWORD`. `herramientas\probar-postgres.ps1` se niega a correr contra otra base.
- **Desarrollo:** clientes compilados con Maven contra `http://localhost:8080`.

## Apagado
`docker compose stop` detiene el servidor y la base y conserva los datos. Para volver a encenderlos: `docker compose start`.

## Despliegue remoto (futuro)
Publique el servidor detrás de HTTPS (proxy inverso), ponga `COOKIE_SECURE=true` y `APP_ALLOWED_ORIGINS=https://su-dominio` y abra los clientes con `-Dservidor=https://su-dominio`. No publique el puerto 5432 a Internet.
