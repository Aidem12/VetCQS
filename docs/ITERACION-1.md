# Iteración 1: Catálogos y núcleo de venta

## Alcance de la semana 4

Esta iteración entrega el primer flujo demostrable del sistema:

1. Consultar productos y servicios por nombre o código de barras.
2. Crear, actualizar y desactivar registros del catálogo.
3. Crear una venta con uno o varios renglones.
4. Calcular subtotal, descuento y total.
5. Cobrar en efectivo, tarjeta, transferencia o una combinación de pagos.
6. Descontar la existencia de productos cuando el cobro se confirma.
7. Mostrar un folio para el ticket.

El módulo clínico, precuentas, cortes de caja, facturación electrónica y autenticación
se mantienen fuera de esta iteración y se implementarán en las iteraciones siguientes.

## API implementada

### Catálogo

```text
GET    /api/productos?q=croquetas
GET    /api/productos/codigo/7501000000011
POST   /api/productos
PUT    /api/productos/{id}
DELETE /api/productos/{id}       # desactivación lógica
```

Ejemplo de alta:

```json
{
  "codigoBarras": "7501000000059",
  "nombre": "Premios para perro",
  "tipo": "PRODUCTO",
  "precio": 99.00,
  "stockActual": 10,
  "stockMinimo": 3,
  "manejaCaducidad": false
}
```

### Venta y cobro

```text
POST /api/ventas
POST /api/ventas/{id}/cobro
```

Crear venta:

```json
{
  "descuento": 0.00,
  "detalles": [
    { "productoId": 1, "cantidad": 1 },
    { "productoId": 2, "cantidad": 2 }
  ]
}
```

Cobrar una venta:

```json
[
  {
    "forma": "EFECTIVO",
    "monto": 479.00,
    "recibido": 500.00
  }
]
```

Para un pago mixto se envían dos elementos en el arreglo y la suma de sus montos
debe ser exactamente igual al total de la venta.

## Decisión temporal de base de datos

La tabla `venta` no exige `turno_id` para una venta pagada durante esta iteración.
El turno se agrega al cobrar cuando se implemente la Iteración 3. Así se evita
introducir un cierre de caja incompleto en el núcleo de ventas.

Si la base ya fue levantada antes del cambio, el esquema debe recrearse:

```bash
docker compose down -v
docker compose up -d --build
```

El comando elimina únicamente el volumen local de PostgreSQL del proyecto.

## Criterios de aceptación

- [ ] El código de barras devuelve un producto activo.
- [ ] Un producto inactivo no aparece en búsquedas ni se puede vender.
- [ ] No se puede crear una venta con existencia insuficiente.
- [ ] El descuento no puede ser mayor que el subtotal.
- [ ] La suma de pagos debe coincidir con el total.
- [ ] El efectivo recibido debe cubrir el monto de efectivo.
- [ ] Al confirmar el cobro, el stock de los productos disminuye.
- [ ] La venta queda con estado `PAGADA` y folio.

