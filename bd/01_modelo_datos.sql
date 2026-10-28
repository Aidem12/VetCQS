-- =============================================================
-- Sistema Veterinaria · Modelo de datos (PostgreSQL)
-- Entregable de la semana 2 · Fase de Elaboración (RUP)
-- 14 tablas: catálogo, consultorio, venta/precuenta, caja,
-- inventario, facturación y bitácora.
-- =============================================================

-- ---------- Usuarios y roles (RF-17) ----------
CREATE TABLE usuario (
    id              SERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    usuario         VARCHAR(40)  NOT NULL UNIQUE,
    contrasena_hash VARCHAR(100) NOT NULL,            -- BCrypt (RNF-03)
    rol             VARCHAR(15)  NOT NULL
                    CHECK (rol IN ('CAJERO', 'VETERINARIO', 'ADMINISTRADOR')),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------- Consultorio (RF-07 a RF-10) ----------
CREATE TABLE duenio (
    id        SERIAL PRIMARY KEY,
    nombre    VARCHAR(120) NOT NULL,
    telefono  VARCHAR(20),
    correo    VARCHAR(120),
    direccion VARCHAR(200)
);

CREATE TABLE mascota (
    id               SERIAL PRIMARY KEY,
    duenio_id        INT          NOT NULL REFERENCES duenio(id),  -- regla 1
    nombre           VARCHAR(60)  NOT NULL,
    especie          VARCHAR(40)  NOT NULL,
    raza             VARCHAR(60),
    sexo             CHAR(1)      CHECK (sexo IN ('M', 'H')),
    fecha_nacimiento DATE,
    peso_kg          NUMERIC(6,2) CHECK (peso_kg > 0)
);

CREATE TABLE consulta (
    id              SERIAL PRIMARY KEY,
    mascota_id      INT          NOT NULL REFERENCES mascota(id),
    veterinario_id  INT          NOT NULL REFERENCES usuario(id),
    fecha           TIMESTAMP    NOT NULL DEFAULT now(),
    motivo          VARCHAR(250) NOT NULL,
    peso_kg         NUMERIC(6,2),
    temperatura_c   NUMERIC(4,1),
    diagnostico     TEXT,
    tratamiento     TEXT
);

-- ---------- Catálogo e inventario (RF-01, RF-12 a RF-14, RF-20) ----------
CREATE TABLE producto (
    id                SERIAL PRIMARY KEY,
    codigo_barras     VARCHAR(40)   UNIQUE,
    nombre            VARCHAR(120)  NOT NULL,
    tipo              VARCHAR(10)   NOT NULL CHECK (tipo IN ('PRODUCTO', 'SERVICIO')),
    precio            NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
    stock_actual      NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (stock_actual >= 0), -- regla 3
    stock_minimo      NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
    maneja_caducidad  BOOLEAN       NOT NULL DEFAULT FALSE,
    activo            BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE lote (
    id           SERIAL PRIMARY KEY,
    producto_id  INT           NOT NULL REFERENCES producto(id),
    numero_lote  VARCHAR(40)   NOT NULL,
    caducidad    DATE          NOT NULL,
    cantidad     NUMERIC(10,2) NOT NULL CHECK (cantidad >= 0),
    UNIQUE (producto_id, numero_lote)
);

-- ---------- Caja: turno y corte ciego (RF-15, RF-19) ----------
CREATE TABLE turno (
    id                 SERIAL PRIMARY KEY,
    cajero_id          INT           NOT NULL REFERENCES usuario(id),
    apertura           TIMESTAMP     NOT NULL DEFAULT now(),
    fondo_inicial      NUMERIC(10,2) NOT NULL CHECK (fondo_inicial >= 0),
    cierre             TIMESTAMP,
    efectivo_contado   NUMERIC(10,2),   -- lo captura el cajero sin ver el esperado
    efectivo_esperado  NUMERIC(10,2),   -- lo calcula el sistema al cerrar
    diferencia         NUMERIC(10,2) GENERATED ALWAYS AS (efectivo_contado - efectivo_esperado) STORED,
    CHECK (cierre IS NULL OR cierre > apertura)
);

-- Solo un turno abierto a la vez (un local, una caja)
CREATE UNIQUE INDEX un_turno_abierto ON turno ((cierre IS NULL)) WHERE cierre IS NULL;

CREATE TABLE salida_efectivo (
    id              SERIAL PRIMARY KEY,
    turno_id        INT           NOT NULL REFERENCES turno(id),
    concepto        VARCHAR(200)  NOT NULL,
    monto           NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    autorizado_por  INT           NOT NULL REFERENCES usuario(id),
    fecha           TIMESTAMP     NOT NULL DEFAULT now()
);

-- ---------- Venta y precuenta (RF-03 a RF-06, RF-11, RF-21) ----------
CREATE TABLE venta (
    id                    SERIAL PRIMARY KEY,
    folio                 VARCHAR(20)   UNIQUE,
    fecha                 TIMESTAMP     NOT NULL DEFAULT now(),
    estado                VARCHAR(10)   NOT NULL DEFAULT 'ABIERTA'
                          CHECK (estado IN ('ABIERTA', 'PRECUENTA', 'PAGADA', 'CANCELADA')),
    consulta_id           INT           UNIQUE REFERENCES consulta(id),  -- 1:0..1
    turno_id              INT           REFERENCES turno(id),            -- se asigna al cobrar
    cajero_id             INT           REFERENCES usuario(id),
    subtotal              NUMERIC(10,2) NOT NULL DEFAULT 0,
    descuento             NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0),
    total                 NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    descuento_autorizado_por INT        REFERENCES usuario(id),
    cancelada_por         INT           REFERENCES usuario(id),
    motivo_cancelacion    VARCHAR(200),
    CHECK (descuento = 0 OR descuento_autorizado_por IS NOT NULL),       -- RF-21
    CHECK (estado <> 'CANCELADA' OR cancelada_por IS NOT NULL)            -- RF-06
    -- El turno se asociará en la Iteración 3; la Iteración 1 permite cobrar
    -- sin corte de caja para validar primero el núcleo del punto de venta.
);

CREATE TABLE venta_detalle (
    id              SERIAL PRIMARY KEY,
    venta_id        INT           NOT NULL REFERENCES venta(id) ON DELETE CASCADE,
    producto_id     INT           NOT NULL REFERENCES producto(id),
    cantidad        NUMERIC(10,2) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
    importe         NUMERIC(10,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED
);

CREATE TABLE pago (
    id        SERIAL PRIMARY KEY,
    venta_id  INT           NOT NULL REFERENCES venta(id),
    forma     VARCHAR(15)   NOT NULL CHECK (forma IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    monto     NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    recibido  NUMERIC(10,2),          -- solo efectivo
    cambio    NUMERIC(10,2) GENERATED ALWAYS AS (recibido - monto) STORED,
    CHECK (forma <> 'EFECTIVO' OR recibido >= monto)
);

CREATE TABLE movimiento_inventario (
    id           SERIAL PRIMARY KEY,
    producto_id  INT           NOT NULL REFERENCES producto(id),
    lote_id      INT           REFERENCES lote(id),
    venta_id     INT           REFERENCES venta(id),
    usuario_id   INT           NOT NULL REFERENCES usuario(id),
    tipo         VARCHAR(12)   NOT NULL
                 CHECK (tipo IN ('ENTRADA', 'VENTA', 'AJUSTE', 'CANCELACION')),
    cantidad     NUMERIC(10,2) NOT NULL CHECK (cantidad <> 0),  -- negativa = salida
    fecha        TIMESTAMP     NOT NULL DEFAULT now()
);

-- ---------- Facturación (RF-16, RF-22) ----------
CREATE TABLE factura (
    id               SERIAL PRIMARY KEY,
    venta_id         INT          NOT NULL UNIQUE REFERENCES venta(id),  -- regla 5
    rfc_receptor     VARCHAR(13)  NOT NULL,
    razon_social     VARCHAR(250) NOT NULL,
    regimen_fiscal   VARCHAR(3)   NOT NULL,
    codigo_postal    VARCHAR(5)   NOT NULL,
    uso_cfdi         VARCHAR(4)   NOT NULL,
    estado           VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE'
                     CHECK (estado IN ('PENDIENTE', 'TIMBRADA', 'ERROR')),
    intentos         INT          NOT NULL DEFAULT 0,
    uuid_sat         UUID         UNIQUE,
    xml              TEXT,
    fecha_solicitud  TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_timbrado   TIMESTAMP,
    CHECK (estado <> 'TIMBRADA' OR uuid_sat IS NOT NULL)
);

-- Solo se factura una venta ya pagada (regla 5)
CREATE FUNCTION fn_factura_solo_pagada() RETURNS trigger AS $$
BEGIN
    IF (SELECT estado FROM venta WHERE id = NEW.venta_id) <> 'PAGADA' THEN
        RAISE EXCEPTION 'Solo se puede facturar una venta pagada';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_factura_solo_pagada
    BEFORE INSERT ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_factura_solo_pagada();

-- ---------- Bitácora (RF-23) ----------
CREATE TABLE bitacora (
    id           SERIAL PRIMARY KEY,
    usuario_id   INT          NOT NULL REFERENCES usuario(id),
    autorizo_id  INT          REFERENCES usuario(id),
    accion       VARCHAR(40)  NOT NULL,   -- CANCELACION, DESCUENTO, AJUSTE, CORTE, SALIDA_EFECTIVO
    tabla        VARCHAR(40)  NOT NULL,
    registro_id  INT          NOT NULL,
    detalle      TEXT,
    fecha        TIMESTAMP    NOT NULL DEFAULT now()
);

-- ---------- Índices para búsquedas frecuentes ----------
CREATE INDEX ix_producto_nombre   ON producto (lower(nombre));        -- RF-02
CREATE INDEX ix_mascota_duenio    ON mascota (duenio_id);
CREATE INDEX ix_consulta_mascota  ON consulta (mascota_id, fecha DESC); -- RF-10
CREATE INDEX ix_venta_estado      ON venta (estado);                   -- precuentas pendientes
CREATE INDEX ix_venta_turno       ON venta (turno_id);
CREATE INDEX ix_lote_caducidad    ON lote (caducidad);                 -- RF-20
CREATE INDEX ix_factura_estado    ON factura (estado);                 -- cola RF-22

-- ---------- Vista de alertas de stock mínimo (RF-14) ----------
CREATE VIEW v_alerta_stock AS
SELECT id, nombre, stock_actual, stock_minimo
FROM producto
WHERE tipo = 'PRODUCTO' AND activo AND stock_actual <= stock_minimo;
