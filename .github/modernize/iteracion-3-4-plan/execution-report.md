# Ejecución Iteraciones 3 y 4

## Alcance y rama
- Rama: `pruebas/iteracion-2` (no se modificó `main`).
- Plan: `plan.md`; tareas aprobadas: `.metadata/tasks.json`.
- Fecha: 2026-10-31/2026-11-01 (hora local).

## Estado de tareas
| ID | Estado | Evidencia |
|---|---|---|
| 001 baseline/regresión | Completada | `baseline-regression.md`, compilaciones y smoke de salud |
| 002 inventario transaccional | Completada | `MovimientoInventario`, `InventarioService`, bloqueo pesimista, migración `03_iteracion_3_4.sql` |
| 003 cortes ciegos | Parcial | El esquema base ya contiene `turno` y restricciones; no se añadió UI JavaFX ni controlador completo en esta ejecución |
| 004 identidad/roles | Completada | login BCrypt, empleados, desactivación lógica, hash ignorado en JSON |
| 005 CFDI simulado | Completada | `Factura`, API idempotente y UUID determinista con marca `mock` |
| 006 seguridad/configuración | Completada | dependencias Spring Security, configuración por entorno, cookies y HTTPS-ready |
| 007 validación final | Completada con limitaciones | servidor Docker operativo; ambos clientes compilan |

## Cambios principales
- `bd/03_iteracion_3_4.sql`: columnas/indexes aditivos, idempotencia CFDI y procedimiento de recuperación.
- Servidor: entidades/repositorios/servicios/controladores de inventario, empleados, autenticación y factura simulada.
- Cobro: deducción de stock y registro de movimiento dentro de la transacción; bloqueo `PESSIMISTIC_WRITE`.
- `application.properties`, `.env.ejemplo`, `docker-compose.yml`: secretos, URLs, puerto, orígenes y cookie externalizados.
- Prueba `MockCfdiIdTest`.

## Resultados de prueba
- `mvn -B test` en `servidor` con JDK 25 activo (release 21): PASS, 1 ejecutada, 0 fallos.
- `mvn -B -DskipTests package` en `cliente-caja`: PASS.
- `mvn -B -DskipTests package` en `cliente-consultorio`: PASS.
- `docker compose build servidor`: PASS usando Maven/Temurin 21.
- `GET http://localhost:8080/api/salud`: HTTP 200.
- Flujo runtime: crear empleado HTTP 201; login correcto HTTP 200; contraseña incorrecta HTTP 401.
- Docker daemon: disponible; PostgreSQL 16 saludable y servidor Java 21 iniciado.

## Seguridad
- BCrypt para nuevas contraseñas; nunca se devuelve `contrasena_hash`.
- Desactivación lógica conserva referencias históricas.
- No se agregaron PAC/SAT, certificados ni credenciales fiscales.
- `SecurityConfig` mantiene APIs históricas públicas para compatibilidad de clientes; se requiere migrar clientes a sesión/token antes de cerrar autorización por endpoint.
- CVE: no se observó scanner Maven instalado en este entorno; la dependencia Spring Security quedó alineada con Spring Boot 3.3.5. Ejecutar escáner SCA en CI.

## Visual y migraciones
- No se modificaron fuentes JavaFX ni estilos de cobro; preservación visual por diff de archivos y compilación de ambos clientes.
- La migración 03 es idempotente. El volumen Docker existente requirió aplicar columnas manualmente para validar runtime; instalaciones nuevas reciben scripts montados.

## Limitaciones y riesgos
- Corte ciego completo (servicio/API/UI) no quedó implementado en esta pasada; las tablas base existen, pero requiere tarea dedicada antes de declarar cobertura total.
- Usuarios seed históricos tienen `PENDIENTE_BCRYPT`; deben rotarse por administración antes de producción.
- No se ejecutó Playwright/automatización visual ni pruebas contra una base limpia.
- JDK predeterminado de Maven local era 17; se usó JDK 25 para compilar `--release 21`. Docker valida con Java 21.

## Test Results
- Command: `mvn -B test` (servidor); Passed: 1; Failed: 0; Skipped: 0.
- Command: `mvn -B -DskipTests package` (clientes); Passed: 2 builds; Failed: 0.
- Command: `docker compose build servidor`; Passed: 1; Failed: 0.
- Runtime probes: salud 200, login válido 200, login inválido 401.
