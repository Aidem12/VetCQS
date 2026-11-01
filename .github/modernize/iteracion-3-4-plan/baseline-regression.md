# Baseline y regresión

Casos congelados antes de cambios de Iteraciones 3/4:

1. `servidor`: `GET /api/salud` responde 200 JSON con `estado=OK`.
2. `servidor`: búsqueda de productos conserva `GET /api/productos?q=`.
3. `cliente-caja`: no se alteraron clases ni estilos de cobro; compila con Maven.
4. `cliente-consultorio`: no se alteraron clases; compila con Maven.
5. Base: PostgreSQL 16, scripts `01_modelo_datos.sql` y `02_datos_prueba.sql`.

La ejecución posterior confirmó salud 200 y compilación de ambos clientes. No se
dispuso de captura gráfica automatizada; la verificación visual queda pendiente
de una sesión JavaFX con entorno gráfico.
