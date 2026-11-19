# Trabajo en VetCQS

- Consultar primero este archivo y `docs/agent-context.md`.
- Proyecto escolar: Java 21, Maven, JavaFX, Spring Boot/Security/Data JPA y PostgreSQL 16.
- `cliente-portal`: login y administración (`AdminView`); abre Caja o Consultorio según rol. `cliente-administracion`: módulo auxiliar existente. `cliente-caja`: ventas, pagos y turnos. `cliente-consultorio`: dueños, mascotas, consultas y precuentas. `servidor`: API y persistencia. `bd`: SQL. `interfaz-comun`: CSS compartido.
- Conservar contratos entre módulos, autenticación por sesión y roles. Contraseñas de usuarios con BCrypt; facturación de demostración.
- Trabajar en `test/estabilizacion`; revisar estado y diferencias antes de integrar. No cambiar main ni crear commits/push sin autorización explícita para esa tarea.
- `.env` es privado: no imprimir ni documentar valores, recrearlo, reemplazarlo, eliminarlo o versionarlo. Verificar solo nombres de variables y comprobaciones sin revelar valores. Mantener `respaldos/` excluido de Git.
- Conservar trabajo local. Integrar por avance directo cuando sea compatible; ante conflictos o riesgo importante para datos, detener la integración y explicar opciones.
- Prohibido usar reset --hard, clean -fd, force push o docker compose down -v. No reiniciar servicios ni modificar la base existente sin necesidad y autorización.
- Leer solo archivos y diffs pertinentes con rg; excluir target, binarios y logs extensos. Evitar repetir exploración, compilaciones o explicaciones.
- Ejecutar pruebas focalizadas y compilar módulos afectados. No afirmar validación real de BD o UI si no se realizó. No añadir funcionalidades ni arreglos grandes no solicitados.
- Actualizar continuidad solo ante novedades relevantes; mantener documentación y respuestas concisas (generalmente 10–15 líneas).

- Pruebas de integración: solo base independiente vetcqs_test; nunca escribir ni reiniciar la BD real para probar. El script exige configuración de test explícita y no lee .env.
- Guardar avances por bloque en docs/agent-context.md para recuperación tras interrupciones; incluir resultados pendientes sin credenciales.
