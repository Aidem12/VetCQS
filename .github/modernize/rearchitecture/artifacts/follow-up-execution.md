# Follow-up execution — blind cash, sessions, seeds and verification

## Scope delivered

- Added `Turno` persistence, repository locking and `TurnoService` with blind
  close calculation (`fondo inicial + efectivo cobrado`), counted cash and
  difference; expected cash is never accepted from the cashier.
- Added `POST /api/turnos`, `GET /api/turnos/abierto`, and
  `POST /api/turnos/cierre`. Cobros now require an open turn and associate the
  sale with that turn.
- Login now establishes an HTTP session (`usuarioId`, `rol`) and logout
  invalidates it. The caja JavaFX HTTP client uses a cookie manager so
  `JSESSIONID` is retained across calls without changing the cobro layout.
- Replaced plaintext-like `PENDIENTE_BCRYPT` seed markers with inactive
  `DISABLED_SEED_NO_PASSWORD` records. Seed users cannot log in; an
  administrator must create/rotate credentials.
- Extended the additive migration with idempotent turn indexes/columns for
  existing PostgreSQL volumes.

## Verification

- `mvn -B test` (`servidor`, JDK 25 targeting release 21): PASS — 1 test, 0 failures.
- `mvn -B -DskipTests package` (`cliente-caja`): PASS.
- `mvn -B -DskipTests package` (`cliente-consultorio`): PASS.
- `docker compose build servidor`: PASS; Docker daemon and PostgreSQL 16 were
  healthy.
- Runtime: `GET /api/salud` 200; `GET /api/turnos/abierto` 200; created an
  active test cashier, opened a $500 turn and closed with $500, yielding
  expected `$500` and difference `$0`.
- Dependency audit attempted with
  `mvn -B org.owasp:dependency-check-maven:check -DskipTest=true -DfailOnError=false`.
  The plugin downloaded successfully, but no scanner result is claimed here;
  CI should retain the generated report and enforce an approved NVD mirror/cache.

## Limitations

- Docker runtime volume was already initialized; additive migration scripts do
  not run automatically on existing volumes. Apply `bd/03_iteracion_3_4.sql`
  through the deployment migration step before startup.
- The default local Maven shell resolves JDK 17; builds used
  `JAVA_HOME=C:\Program Files\Java\jdk-25` because the project targets Java 21.
- No browser/JavaFX visual automation was available; cobro source/style files
  were not redesigned.

## Test Results

- Passed: server 1, caja build 1, consultorio build 1, runtime probes 3.
- Failed: 0.
- Skipped: client tests (no client test sources).
