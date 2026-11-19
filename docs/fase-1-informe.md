# VetCQS — Informe de Fase 1

Fecha: 18 de noviembre de 2026. Trabajo exclusivamente local en `test/estabilizacion`.

## Resultado de accesos
| Cuenta | Administración/personal/inventario | Caja | Consultorio | Consultar productos |
|---|---|---|---|---|
| ADMINISTRADOR | Sí | Sí, turno propio | Sí, identidad propia | Sí |
| CAJERO | No | Sí, turno propio | No | Sí |
| VETERINARIO | No | No; puede generar precuenta clínica | Sí | Sí |

ADMINISTRADOR inicia en un panel de tres áreas, vuelve y cambia sin iniciar sesión otra vez. Su rol permanece intacto. Los demás usuarios siguen entrando directamente a su área. Todos pueden cerrar sesión. Cambios de rol o desactivaciones invalidan sesiones existentes al siguiente acceso HTTP.

## Cambios y problemas corregidos
- Navegación administrativa y permisos compatibles con una sola cuenta; límites entre propietarios de turnos y autorizaciones sensibles conservados.
- Verificación de usuario activo/rol en solicitudes y limpieza de autenticación tras login fallido.
- HTTP500 reproducido al consultar movimientos: se intentaban serializar relaciones JPA fuera de sesión. Carga explícita de producto/usuario; consulta de historial aprobada contra PostgreSQL.
- Orden estable y snapshot coherente del kardex; bloqueo de edición/baja de producto para conservar existencias concurrentes.
- Validación de cantidades positivas de precuentas y precisión decimal de ventas/productos/movimientos, antes de llegar al esquema NUMERIC(10,2).
- Facturación idempotente ligada a venta, prevención de segunda factura mediante bloqueo y validación básica de formato fiscal. Sigue siendo demostración; no timbra ni verifica datos ante SAT.
- Cierre de ventana bloqueado durante cobro para no abandonar una operación en curso.
- Script de integración deja de leer configuración real; exige base independiente y falla si falta la configuración explícita.

## Pruebas
| Validación | Resultado |
|---|---|
| Baseline antes de cambios | 22 aprobadas; 1 integración omitida |
| Unitarias/mocks finales | 32 aprobadas |
| Integración HTTP/PostgreSQL16 aislado | 1 escenario ampliado aprobado, con numerosas aserciones |
| Compilación Java21 de Portal/Caja/Consultorio | Aprobada |
| Recorrido JavaFX con API simulada | Aprobado |
| git diff --check | Aprobado |
| Integridad privada de .env | Sin cambios |

Las 33 pruebas finales se verificaron en ejecuciones combinadas y focales; se reutilizó el resultado de 32 unitarias tras corregir solamente la sincronización de conexión WebSocket en la integración. La prueba espera ahora a que el servidor registre el socket antes de enviar; el transporte real pasó. No quedan fallos conocidos de las pruebas ejecutadas.

Cobertura funcional comprobada: credenciales correctas/incorrectas, logout, desactivación, roles y endpoints, expiración usando el mecanismo real de Tomcat (sin esperar el temporizador de inactividad), identidad administrativa en consulta/turno; personal; productos y edición sin cambiar stock; entradas/ajustes/kardex/alertas; dueño/mascota/consulta/historial; precuentas y duplicados; autorizaciones y cancelación; cobros en efectivo/tarjeta/transferencia/mixto, cobro duplicado y rollback cuando cambia stock; denominaciones/retiros/cortes/diferencias/historial/reportes; facturación simulada, formato e idempotencia; WebSocket real y restricción por rol.

En JavaFX: mismo token al entrar/regresar de las tres áreas ADMIN; logout desde cada área; entrada directa de los demás roles; registro clínico, inventario, tablas y recuperación de errores. Se ejecuta contra API simulada, no contra datos reales.

Omitido deliberadamente: escrituras/pruebas contra base clínica, reinicios de servicios reales, despliegue, pruebas de carga/concurrencia exhaustivas, recorrido JavaFX completo con backend real, CFDI real y medición de cobertura porcentual. No afirmar que estas comprobaciones pasaron.

## Seguridad y prioridades pendientes
1. **Antes de acceso remoto:** CSRF continúa deshabilitado. Adaptar clientes y servidor juntos a tokens CSRF; verificar HTTPS y cookie Secure. Mantener HttpOnly/SameSite y añadir limitación de intentos de login/autorización. Esta fase no certifica seguridad de producción.
2. **Continuidad y auditoría:** impedir desactivar el último administrador; ampliar bitácora a personal, productos y facturas. Revisar política de múltiples sesiones porque el login manual no registra la estrategia de concurrencia declarada.
3. **Avisos automáticos:** WebSocket responde y transmite en prueba, pero el negocio no publica eventos ni Caja se suscribe actualmente. Precuentas operativas por REST/F1. Antes de conectar avisos, gestionar revocación de sockets tras logout/desactivación/expiración y reconexiones.
4. **Escala y límites:** pruebas simultáneas de apertura/cobro/edición y facturación; paginar catálogos/historiales; validar máximos monetarios agregados y restauración de respaldos.
5. **Cobertura:** conviene JaCoCo en perfil opcional Maven, conservando el agente ByteBuddy y midiendo primero un baseline. No se añadió una dependencia ni un porcentaje sin medir. [Plugin oficial](https://www.jacoco.org/jacoco/trunk/doc/maven.html); [compatibilidad Java21 desde 0.8.11](https://www.jacoco.org/jacoco/trunk/doc/changes.html).

## Git y operación
PR #3 integrado, HEAD 2d857f3; remoto de test sin divergencia al inspeccionar. Main permanece sin cambios. No commits, push, cambios de .env, SQL, entidades, POM o Docker Compose. Código modificado localmente; backend de Docker no actualizado. Para activar esta versión se requiere una actualización explícita posterior del servidor.

Archivos modificados seguidos por Git:
- `cliente-caja/src/main/java/mx/buap/veterinaria/caja/CajaApp.java`
- `cliente-consultorio/src/main/java/mx/buap/veterinaria/consultorio/ConsultorioApp.java`
- `cliente-portal/src/main/java/mx/buap/veterinaria/portal/AdminView.java`
- `cliente-portal/src/main/java/mx/buap/veterinaria/portal/PortalApp.java`
- `herramientas/probar-postgres.ps1`
- `herramientas/pruebas-ui/FlujoClinicoTest.java`
- `servidor/src/main/java/mx/buap/veterinaria/config/SecurityConfig.java`
- `servidor/src/main/java/mx/buap/veterinaria/controller/AuthController.java`
- `servidor/src/main/java/mx/buap/veterinaria/controller/FacturaController.java`
- `servidor/src/main/java/mx/buap/veterinaria/controller/ProductoController.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/AjusteRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/FacturaRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/MovimientoRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/PrecuentaRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/ProductoRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/dto/VentaRequest.java`
- `servidor/src/main/java/mx/buap/veterinaria/repository/MovimientoInventarioRepository.java`
- `servidor/src/main/java/mx/buap/veterinaria/service/InventarioService.java`
- `servidor/src/main/java/mx/buap/veterinaria/service/TurnoService.java`
- `servidor/src/test/java/mx/buap/veterinaria/AuthControllerTest.java`
- `servidor/src/test/java/mx/buap/veterinaria/ClinicaFlowTest.java`
- `servidor/src/test/java/mx/buap/veterinaria/FlujoPostgresTest.java`
- `servidor/src/test/java/mx/buap/veterinaria/InventarioIteracion3Test.java`

Archivos nuevos: `servidor/src/test/java/mx/buap/veterinaria/EstabilizacionTest.java`, `AGENTS.md`, `docs/agent-context.md` y este informe. AGENTS/contexto ya estaban sin seguimiento al iniciar esta fase y se conservaron/actualizaron.

## Preparación de Fase 2
Acceso administrativo y regresiones documentados; base de trabajo lista para definir agenda una vez autorizado. Antes de implementarla, acordar duración, horarios, disponibilidad, asignación y conflictos; diseñar migración y pruebas aisladas. Planes de Fases2–6 guardados en `docs/agent-context.md`; ninguna implementada en esta fase.
