# Iteración 3-4 runtime

El volumen PostgreSQL existente no reejecuta scripts de `/docker-entrypoint-initdb.d`; las migraciones aditivas deben ejecutarse mediante un mecanismo versionado en despliegues existentes.

## What Happened
La imagen compiló con Java 21, pero el servidor falló inicialmente por columnas nuevas ausentes. Aplicar `03_iteracion_3_4.sql` permitió validar JPA y runtime.

## Takeaway
Mantener scripts idempotentes y documentar explícitamente la aplicación sobre volúmenes existentes; no depender solo de initdb.

## History
- 2026-10-31 (sistema-veterinaria/iteracion-3-4): initial
