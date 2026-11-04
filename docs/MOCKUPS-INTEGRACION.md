# Integración de mockups

Se conserva el acceso por usuario y contraseña y el tema azul/verde de VetCQS.

## Flujos disponibles

- Caja: captura por código, precuentas médicas (F1), resumen, cobro (F5) y medios efectivo, tarjeta, transferencia o mixto. Los pagos electrónicos se registran; no se conecta una terminal bancaria.
- Las ventas guardadas se recuperan desde pendientes y se cobran usando su identificador original.
- Autorización administrativa: descuento, precio, eliminación de renglón y cancelación de **ventas pendientes**. Las credenciales se validan en el servidor y se registra una bitácora sin contraseñas. No incluye devolución de ventas pagadas.
- Turno: fondo inicial, retiros autorizados, conteo por denominaciones y comprobantes, corte ciego, histórico y reporte. El saldo esperado se revela después del cierre. Tarjetas y transferencias se separan del efectivo. El reporte permite guardar TXT e imprimir; PDF depende de una impresora PDF del sistema.
- Consultorio: selección por nombre desde el catálogo de servicios e insumos, detalle con nombre/cantidad/importe y consulta de existencias antes de enviar la precuenta.
- Facturación: captura opcional de datos fiscales después del pago, marcada como demostración. No timbra con SAT/PAC ni envía correos.

## Puesta en marcha

Reinicie el servidor y abra cliente-portal con Java 21. Abra un turno con el cajero autenticado antes de cobrar. Se utilizan las tablas existentes salida_efectivo y bitacora del esquema 01 y las columnas del esquema 03; no se ejecutó ninguna migración contra una base real.

## Alcance de las referencias

No se copian los nombres de usuarios ni los importes de las imágenes. No se agrega automáticamente IVA del 16%: el catálogo actual no guarda una tasa fiscal por producto. El acceso por PIN se descartó por preferencia del usuario. La apertura física del cajón, el timbrado real, la asignación de lotes y el envío de correo necesitan su integración correspondiente.

## Verificación

Pruebas del servidor para sesión, clínica, pagos mixtos, autorización y corte. Recorrido JavaFX con API aislada para login/logout y registro clínico; capturas de las ventanas de Caja, pago y corte. Las pruebas no realizan cobros ni alteran la base real.
