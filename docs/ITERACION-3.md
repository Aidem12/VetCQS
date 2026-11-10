# Iteración 3: control de stock y cortes de caja (semana 6)

Rama: `feature/semana6-stock-cortes`, hecha sobre `test/estabilizacion` con el diseño del PR #1.

## Qué ya existía (estabilización)

- Descuento de existencias al cobrar, con bloqueo de fila y movimiento `VENTA` en el kardex.
- Entradas de inventario desde Administración (`POST /api/inventario/{id}/entradas`).
- Apertura de turno por denominaciones, salidas de efectivo, corte ciego, reporte e historial de cortes.

## Qué se agregó en la semana 6

### Control de stock

- **Alertas de stock mínimo (RF-14).** `GET /api/inventario/alertas` devuelve los productos activos
  cuya existencia está en o por debajo de su mínimo, con lo que falta para llegar a él.
  Administración muestra un aviso arriba de la tabla y los productos en rojo.
- **Ajustes de inventario.** `POST /api/inventario/{id}/ajustes` registra un movimiento `AJUSTE`
  con cantidad con signo: merma, caducidad o corrección por conteo físico. Rechaza la cantidad cero,
  los servicios y cualquier ajuste que deje la existencia en negativo (409).
- **Kardex.** `GET /api/inventario/{id}/kardex` lista los movimientos del producto, del más reciente
  al más antiguo, con la existencia que quedó después de cada uno. En Administración: botón
  **Ver movimientos**.
- **Trazabilidad.** Al editar un producto ya no se puede cambiar su existencia; solo cambia con
  entradas, ventas o ajustes, para que todo quede registrado en el kardex.
- **Caja.** La ventana de inventario marca en rojo los productos con existencia baja y dice cuántos son.

### Diseño final de pantallas y atajos de teclado

| Pantalla | Atajos |
| --- | --- |
| Caja | F1 precuenta · F2 buscar producto · F4 inventario · F5 cobrar · Enter agregar · Supr quitar renglón |
| Consultorio | F2 buscar dueño · F4 inventario · Ctrl+S guardar consulta · F10 enviar precuenta |
| Administración | F5 actualizar |

Los botones muestran su atajo (por ejemplo, `Cobrar · F5`) y cada pantalla tiene una barra con la lista.

## API nueva

| Método | Ruta | Rol | Uso |
| --- | --- | --- | --- |
| GET | `/api/inventario/alertas` | Administrador | Productos en o bajo el stock mínimo |
| POST | `/api/inventario/{id}/ajustes` | Administrador | Ajuste con signo; cuerpo `{cantidad, usuarioId, motivo}` |
| GET | `/api/inventario/{id}/kardex` | Administrador | Movimientos con existencia resultante |

No hay cambios en la base de datos: se usan la columna `stock_minimo` y el tipo `AJUSTE`
que ya existían en `01_modelo_datos.sql`.

## Pruebas

- `servidor/src/test/java/mx/buap/veterinaria/InventarioIteracion3Test.java`: ajuste que resta,
  rechazo de existencia negativa, rechazo de cero y de servicios, cálculo del faltante y saldos del kardex.
- `herramientas/pruebas-ui/FlujoClinicoTest.java`: ahora encuentra los botones aunque muestren su atajo.

Ejecutar: `mvn -f servidor/pom.xml test`.

## Archivos modificados

- Servidor: `InventarioController`, `InventarioService`, `ProductoController`, `ProductoRepository`,
  DTO nuevos `AjusteRequest`, `AlertaStock`, `MovimientoKardex`.
- Clientes: `AdminView` (portal), `CajaApp`, `ConsultorioApp`.
- Estilos: `interfaz-comun/.../veterinaria.css` (aviso de stock y renglones en rojo).
