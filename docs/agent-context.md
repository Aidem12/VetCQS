# Continuidad — 2026-11-18 — Fase 1

## Estado y protección
- Copia local abierta en VS Code: C:/Users/josev/OneDrive/Documents/Veterinaria/sistema-veterinaria. Workspace apunta a Veterinaria padre.
- Rama test/estabilizacion, HEAD 2d857f3 (PR #3 integrado previamente por avance directo); fetch confirmó 0/0 frente al remoto. Main/origin-main permanecen en 0e2cd9a. No commits ni push autorizados/realizados.
- Cambios de Fase1 locales sin commit, más AGENTS.md y documentación sin seguimiento. Conservarlos; revisar git status antes de continuar.
- .env idéntico a referencia SHA256 privada y excluido/no seguido. Nunca mostrar valores, sustituirlo ni publicarlo. Base y contenedores reales vet-bd/vet-servidor no modificados/reiniciados.

## Fase 1 implementada
- Portal ADMINISTRADOR ofrece Administración, Caja y Consultorio. Regreso al panel verifica sesión en servidor; cambiar de módulo no cambia rol ni cuenta. Logout desde cada módulo invalidado. CAJERO/VETERINARIO mantienen entrada directa y permisos limitados.
- Administración añade regreso; Caja/Consultorio muestran regreso solo tras consultar rol real. Confirman descarte de captura local; Caja impide cerrar ventana durante cobro.
- SecurityConfig habilita funciones clínicas/caja al administrador y valida usuario activo/rol en cada solicitud. Desactivación/cambio de rol invalida sesiones existentes; login fallido limpia autenticación anterior. TurnoService permite turno propio ADMIN y conserva límites entre operadores.
- Error confirmado HTTP500 del historial de inventario corregido con EntityGraph. Kardex ordena fecha+ID y lee snapshot REPEATABLE_READ. Editar/desactivar productos usa bloqueo transaccional para no sobrescribir existencias concurrentes.
- Cantidades de venta/precuenta, productos, entradas y ajustes validan precisión de dos decimales; precuentas exigen cantidad positiva. Persistencia no recibe fracciones que el esquema redondearía.
- Facturas de demostración: bloqueo por venta, idempotencia ligada a esa venta y rechazo de duplicados, validación de formato RFC/régimen/CP/uso. No validación SAT ni CFDI real.
- FlujoPostgresTest y herramientas/probar-postgres.ps1 exigen base independiente vetcqs_test con puerto explícito; ya no usan .env ni base clínica por defecto. No cambios de SQL, entidades, POM ni Docker Compose.

## Validación y recuperación
- Baseline: 22 aprobadas +1 integración omitida, antes de cambios.
- Final: 32 pruebas unitarias/mocks aprobadas; 1 integración HTTP/PostgreSQL ampliada aprobada en ejecución focal final; cero fallos pendientes. Se reutilizó el resultado unitario válido al corregir únicamente la sincronización de la prueba WebSocket.
- Integración: roles, empleados, desactivación, expiración real de sesiones Tomcat, propietarios/mascotas/consultas, trazabilidad ADMIN, precuentas/duplicados, inventario/kardex/alertas, ventas/4 modalidades de pago, autorización/cancelación, turnos propios/ajenos, retiro/corte/diferencia/reportes, facturas/idempotencia/formato; rollback sin pagos persistidos cuando falta stock. WebSocket real probado: handshake/envío y rechazo a veterinario.
- Portal/Caja/Consultorio compilados Java21. Harness JavaFX con API simulada aprobado: 3 áreas ADMIN sin relogin, regreso/logout desde todas, flujo clínico, tablas, inventario y errores. No prueba visual completa JavaFX contra servidor real ni pruebas de carga.
- Logs fuera del repo: C:/Users/josev/Documents/Codex/2026-11-03/cua/work/phase1-final.log (32 unitarias OK; carrera de test WS luego corregida), phase1-transport.log (integración final OK), phase1-ui-final.log (UI OK). Filtrar resultados; no imprimir logs completos (Spring genera credenciales efímeras de test).
- Runner temporal work/phase1-maven.ps1 admite -Module, -Goal, -Tests, -Postgres; requiere secreto temporal cifrado para integración. NO usar antiguo work/build-vet.ps1 (apuntaba a BD real).
- Contenedor independiente de prueba vetcqs-phase1-test, puerto55432, tmpfs, etiqueta vetcqs.purpose=phase1-test; limpieza al concluir registrada abajo. No confundirlo con contenedores reales.

## Límites y prioridades
- P1 antes de despliegue remoto: activar CSRF con protocolo de token compartido por clientes; HTTPS/cookie Secure; limitar intentos de login y autorización. Conservados SameSite=Lax/HttpOnly y BCrypt; no activar CSRF de golpe sin adaptar clientes.
- P2: canal WebSocket probado pero avisos automáticos de negocio aún no conectados a productor/cliente. Flujo vigente de precuentas usa REST/F1. Antes de conectarlo, revocar sockets por logout/expiración/desactivación y reconectar con sesión válida.
- P2: auditar altas/bajas de personal/productos y facturación; proteger último administrador; revisar política real de sesiones concurrentes (login manual no registra estrategia de concurrencia). Auditoría actual de operaciones sensibles/turnos e inventario conservada.
- P2: concurrencia/carga, paginación y revisión de sumas máximas agregadas. No afirmación de seguridad exhaustiva.
- JaCoCo conviene como perfil Maven opcional prepare-agent/report, compatible Java21; no instalado ni medido. Integrar sin sobrescribir argLine de ByteBuddy y sin umbral arbitrario hasta tener baseline.
- No se desplegó backend actualizado. Probar versión de servidor correspondiente antes de usar nuevos clientes. Informe completo: docs/fase-1-informe.md.

## Plan futuro (NO implementado)
- Fase2: agenda; definir duración/horarios/disponibilidad, asignación de veterinario, conflictos, reprogramación y recordatorios. Acordar modelo y permisos; migración y pruebas aisladas antes de UI.
- Fase3: expediente completo; vacunas, desparasitación, alergias, antecedentes, recetas, estudios, adjuntos y reglas de acceso.
- Fase4: lotes/caducidad, proveedores, costos y reabastecimiento; migración compatible con kardex y ventas.
- Fase5: dashboard administrativo de ingresos/ventas/indicadores y exportaciones PDF/Excel con filtros y trazabilidad.
- Fase6: pruebas integrales/carga, endurecimiento de seguridad, respaldo/restauración verificados e instaladores.
- Esperar autorización del usuario para iniciar Fases2–6; no commits/push sin autorización.

- Cierre: contenedor temporal vetcqs-phase1-test eliminado tras verificar su etiqueta, junto con su credencial temporal cifrada. vet-bd y vet-servidor continúan activos. Para repetir integración, crear una nueva base independiente y proporcionar credenciales de test; el runner no debe usar .env.

## Avance implementación integral (rama local feature/integral, 2026-11-18)
Base: origin/test/estabilizacion 25c9a57 + commits locales (sin push). Punto de guardado: ver `git log feature/integral`.
### Hecho y probado (67 pruebas en verde, incluida IntegralPostgresTest y FlujoPostgresTest con PostgreSQL independiente)
- Flyway (baseline 1, V2..V6 aditivas): agenda, expediente, farmacia/lotes, devoluciones, bitácora (usuario_texto, ip), aviso_caja.
- Agenda (servidor + ventana Consultorio F3 día/semana/mes + configuración en Administración): traslapes por EXCLUDE gist, reserva concurrente (1 gana, 5 reciben 409).
- Expediente (servidor + ventana Consultorio): vacunas, preventivos, antecedentes/alertas, notas, recetas PDF, estudios, adjuntos PDF/PNG/JPEG ≤5 MB (415 si no), historial PDF; anulación con motivo, sin borrado.
- Farmacia (servidor + pestaña Administración): proveedores, compras con lote, FEFO, bloqueo de caducados, ajustes por lote, caducidades, reposición, márgenes.
- Devoluciones (servidor + Caja F6): autorización admin, prorrateo de descuento, reingreso a lote, resta del efectivo esperado del corte.
- Indicadores (servidor + pestaña Administración): consultas agregadas, conciliado con venta/pago/devolucion; PDF y Excel con folio/filtros/responsable.
- Seguridad: CSRF (cookie XSRF-TOKEN + cabecera X-XSRF-TOKEN en todos los clientes; login exento), 401 sin sesión / 403 sin permiso, límite 5 fallos/15 min → bloqueo 5 min (login y autorizaciones), una sesión por usuario (SesionesActivas), WebSocket se cierra al terminar la sesión, cabeceras (CSP, nosniff, frame deny, no-referrer), auditoría de accesos (/api/auditoria + pestaña Bitácora), sesión del portal pasa a hijos por variable de entorno VETCQS_SESION.
- Avisos en tiempo real: precuenta → aviso_caja persistido → WebSocket tras commit; Caja con reconexión y recuperación por /api/ventas/avisos.
- Facturación: interfaz ServicioTimbrado + TimbradoDemostracion; aviso "no es CFDI timbrado por el SAT"; auditada.
- Zona horaria del proceso fijada a America/Mexico_City (comun/ZonaHoraria).
- Respaldo/restauración: herramientas/respaldar-bd.ps1 y restaurar-bd.ps1; pg_dump/pg_restore verificados en bases temporales del contenedor de pruebas (conteos iguales).
### Pendiente
- docs/verificacion-integral.md (matriz 1-160), docs de operación Windows y diagramas, informe final.
- Cobertura JaCoCo (perfil cobertura) en ejecución; anotar el número real.
- Recorrido JavaFX contra backend real: no ejecutado (requiere autorización para actualizar el servidor Docker real o un servidor aparte).
- Distribución de clientes sin Maven (jpackage): no implementado.
### Cómo continuar
Compilación en la PC del usuario: `_build/ayudante-compilacion.bat` vigila `_build/pedido.sh` y ejecuta en contenedor maven con red del contenedor vetcqs-pg-pruebas (nunca la BD real). `_build` está fuera de Git.
