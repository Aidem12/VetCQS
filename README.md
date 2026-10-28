# Sistema Veterinaria

Punto de venta con módulo de consultorio para una veterinaria.
Proyecto escolar · BUAP · Jose Antonio Montiel Villegas (202138979) · Eduardo Pérez López (202141284)

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `servidor/` | Spring Boot 3 (Java 21): API REST, WebSocket de avisos a caja, reglas de negocio |
| `cliente-caja/` | JavaFX 21: venta, cobro, corte, impresora y cajón |
| `cliente-consultorio/` | JavaFX 21: dueños, mascotas, consultas y precuentas |
| `bd/` | `01_modelo_datos.sql` (14 tablas) y `02_datos_prueba.sql` |
| `herramientas/prueba-perifericos/` | Prueba de impresora ESC/POS y cajón en Linux (riesgo R3) |
| `docker-compose.yml` | Levanta PostgreSQL 16 y el servidor |

## 1. Preparar el servidor Linux (Ubuntu 24.04 LTS o Debian 12)

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk maven docker.io docker-compose-v2
sudo usermod -aG docker,lp $USER     # cerrar sesión y volver a entrar
```

## 2. Clonar y levantar la base de datos y el servidor

```bash
git clone <url-del-repositorio> sistema-veterinaria
cd sistema-veterinaria
cp .env.ejemplo .env                 # cambiar BD_PASSWORD
docker compose up -d --build
curl http://localhost:8080/api/salud # debe responder {"estado":"OK","tablas":14,...}
```

Los scripts de `bd/` solo se ejecutan la primera vez que se crea el volumen.
Para empezar de cero: `docker compose down -v && docker compose up -d --build`.

## 3. Correr los clientes en cada computadora

```bash
cd cliente-caja
mvn javafx:run -Dservidor=http://IP_DEL_SERVIDOR:8080
```

Igual para `cliente-consultorio`. La barra superior debe decir **Servidor: conectado**.

## 4. Probar impresora y cajón (semana 3)

```bash
cd herramientas/prueba-perifericos
javac PruebaPerifericos.java
ls /dev/usb/                          # buscar lp0 después de conectar la impresora USB
java PruebaPerifericos /dev/usb/lp0   # o IP:9100 si la impresora es de red
```

Anotar el resultado en la tabla de la sección 3 del documento de la semana 3.

## 5. Iteración 1: catálogos y núcleo de venta

El alcance, el plan semanal, los endpoints y los criterios de aceptación están
documentados en [`docs/ITERACION-1.md`](docs/ITERACION-1.md) y
[`docs/PLAN-SEMANA-4.md`](docs/PLAN-SEMANA-4.md).

La API de la iteración 1 permite consultar y administrar el catálogo, crear ventas
y registrar cobros. El turno de caja, el módulo clínico, la facturación y la
autenticación se implementarán en las iteraciones posteriores.

## Trabajo con Git

- `main`: solo versiones entregadas al final de cada iteración.
- `develop`: integración diaria.
- `feature/<modulo>-<tema>` (por ejemplo `feature/caja-cobro`): una rama por tarea; se une a `develop` con *pull request* revisado por otro integrante.
- Mensajes de commit en español y en presente: `Agrega búsqueda por código de barras`.
