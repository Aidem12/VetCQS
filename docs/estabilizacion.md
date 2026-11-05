# Estabilización de VetCQS

Rama de trabajo: `test/estabilizacion`. `main` se conserva hasta validar esta versión.

## Diagnóstico

La interfaz de administración interpretaba un arreglo JSON como una sola fila. Los registros podían existir en PostgreSQL sin aparecer correctamente en las tablas. Ahora cada registro ocupa su fila y administración consulta el catálogo completo, incluidos los productos inactivos. El formulario conserva y permite capturar el código de barras.

El servidor que estaba ejecutándose no correspondía a las funciones nuevas del cliente. Antes de reconstruirlo, `/api/auth/sesion` no devolvía el comportamiento del código actual y las nuevas rutas producían los errores reportados. Tras reconstruir el contenedor, `/api/salud` confirma `versionApi: estabilizacion-1`. Las consultas médicas, precuentas, autorizaciones, cobros y turnos pasan la prueba HTTP con PostgreSQL real.

La seguridad también ocultaba algunos errores al procesar la ruta interna de error. Se permite el despacho de errores y se devuelven mensajes de validación, conflicto y autorización legibles. Un conflicto 409 ya no se presenta siempre como falta de inventario.

No fue necesario borrar o reinicializar la base de datos. Se revisó su esquema y se creó un respaldo antes de actualizar el servidor.

## Cambios de interfaz

- Consultorio: selector de servicios e insumos, tabla con cantidad, nombre e importe; elegir desde inventario agrega a la precuenta. Se quitó el buscador duplicado.
- Caja: apertura por cantidad de billetes y monedas, desde $0.50 hasta $1000, con suma automática y validación del servidor.
- Administración: historial de cortes de todos los cajeros, reporte, impresión y exportación. Se retiró el acceso al historial de la barra del cajero.
- Encabezados: estado de conexión verde y estilo común para cerrar sesión.
- Se mantienen usuario y contraseña, y facturación identificada como demostración.

## Actualización y pruebas

`herramientas/actualizar-servidor.ps1` respalda PostgreSQL en `respaldos/` (excluido de Git), reconstruye solamente el servidor y verifica su versión. No elimina volúmenes ni modifica las credenciales existentes. Después de actualizar, cierre las ventanas del programa y vuelva a abrir el portal para compilar/cargar las interfaces y crear una sesión nueva.

Validación realizada: compilación de los tres clientes; 18 pruebas del servidor sin errores; recorrido JavaFX de login/logout de los tres roles, registros clínicos, tablas de administración, inventario, apertura por denominaciones y adición de insumos.

La prueba `FlujoPostgresTest` levanta un servidor HTTP real y crea un esquema temporal independiente en PostgreSQL. Comprueba alta de personal e inicio de sesión, catálogo, dueño, mascota, consulta e historial, precuenta, autorización de descuento, cobro mixto, stock, rechazo de doble cobro, salida de efectivo, cierre, reporte administrativo y permisos. El esquema temporal se elimina al terminar; los registros de la clínica no se usan para operaciones de prueba.

Para repetirla con JDK 21 y PostgreSQL activo: `powershell -ExecutionPolicy Bypass -File herramientas/probar-postgres.ps1`. Para el recorrido de interfaces: `powershell -ExecutionPolicy Bypass -File herramientas/pruebas-ui/ejecutar.ps1`.
