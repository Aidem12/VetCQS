## [iteracion-3-4] Ejecución Iteraciones 3 y 4
- Se añadió inventario transaccional con bloqueo pesimista, autenticación BCrypt y CFDI simulado idempotente.
- El esquema existente ya tenía turno/corte, pero el flujo completo de corte ciego quedó pendiente.
- Maven local resolvía Java 17 aunque el proyecto exige 21; JDK 25 con `--release 21` permitió pruebas y Docker confirmó Java 21.
- Docker/PostgreSQL estaban disponibles; el volumen existente requirió aplicar la migración aditiva manualmente para runtime.
- Learnings consumidos: (none).
