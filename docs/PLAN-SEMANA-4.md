# Plan del programador: Semana 4

| Día | Actividad | Evidencia |
|---|---|---|
| 1 | Confirmar alcance, revisar modelo y levantar PostgreSQL | `/api/salud` responde correctamente |
| 2 | Completar catálogo de productos y servicios | Endpoints de catálogo probados |
| 3 | Crear venta y cálculo de totales | Venta con folio y detalle |
| 4 | Implementar cobro y pagos mixtos | Venta pagada con formas de pago |
| 5 | Integrar pantalla de caja | Flujo de escaneo/búsqueda y cobro |
| 6 | Ejecutar pruebas de aceptación y corregir errores | Evidencia de casos aceptados |
| 7 | Preparar demostración y documentación | Esta documentación y captura del flujo |

## Fuera de alcance

- Consultas médicas y precuentas.
- Cortes de caja y salidas menores.
- Timbrado fiscal.
- Usuarios, autenticación y permisos.
- Comunicación con impresora térmica y cajón físico.

## Cómo iniciar

Desde la raíz del proyecto:

```bash
cp .env.ejemplo .env
docker compose up -d --build
curl http://localhost:8080/api/salud
```

En Windows PowerShell, la última comprobación puede hacerse con:

```powershell
Invoke-RestMethod http://localhost:8080/api/salud
```

Después se ejecuta la caja:

```bash
cd cliente-caja
mvn javafx:run
```
