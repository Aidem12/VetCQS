# Iteración 2: módulo clínico y precuenta

## Alcance implementado

- Búsqueda y alta de dueños.
- Consulta de mascotas por dueño y alta de mascotas.
- Registro de consultas con motivo, signos básicos, diagnóstico y tratamiento.
- Historial de consultas por mascota.
- Creación de una precuenta vinculada a una consulta.
- Envío de la precuenta a caja mediante una venta en estado `PRECUENTA`.
- La venta conserva el vínculo `consulta_id` y puede cobrarse desde la API de ventas.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/clinica/duenos?q=` | Buscar dueño por nombre o teléfono |
| POST | `/api/clinica/duenos` | Registrar dueño |
| GET | `/api/clinica/duenos/{id}/mascotas` | Listar mascotas |
| POST | `/api/clinica/duenos/{id}/mascotas` | Registrar mascota |
| GET | `/api/clinica/mascotas/{id}/consultas` | Consultar historial |
| POST | `/api/clinica/consultas` | Registrar consulta |
| POST | `/api/ventas/precuentas` | Crear precuenta vinculada |

La precuenta no descuenta inventario al enviarse. El descuento ocurre cuando caja confirma
el cobro, igual que una venta normal.

## Cliente

El cliente `cliente-consultorio` permite buscar dueños, seleccionar mascotas, registrar
consultas y enviar productos o servicios por ID a la precuenta.

Para ejecutarlo:

```powershell
cd cliente-consultorio
& "$env:USERPROFILE\Tools\apache-maven-3.10.0\bin\mvn.cmd" clean javafx:run
```

La interfaz requiere que el servidor responda en `http://localhost:8080`.
