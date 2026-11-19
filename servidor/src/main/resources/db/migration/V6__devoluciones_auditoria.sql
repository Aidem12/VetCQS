-- Devoluciones/reembolsos controlados y auditoría de accesos (cambios aditivos).

CREATE TABLE IF NOT EXISTS devolucion (
    id               SERIAL PRIMARY KEY,
    folio            VARCHAR(20)   NOT NULL UNIQUE,
    venta_id         INT           NOT NULL REFERENCES venta(id),
    turno_id         INT           REFERENCES turno(id),          -- turno donde salió el reembolso
    usuario_id       INT           NOT NULL REFERENCES usuario(id),
    autorizado_por   INT           NOT NULL REFERENCES usuario(id),
    motivo           VARCHAR(200)  NOT NULL,
    forma_reembolso  VARCHAR(15)   NOT NULL CHECK (forma_reembolso IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    monto            NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    fecha            TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_devolucion_venta ON devolucion (venta_id);
CREATE INDEX IF NOT EXISTS ix_devolucion_fecha ON devolucion (fecha);

CREATE TABLE IF NOT EXISTS devolucion_detalle (
    id             SERIAL PRIMARY KEY,
    devolucion_id  INT           NOT NULL REFERENCES devolucion(id),
    producto_id    INT           NOT NULL REFERENCES producto(id),
    cantidad       NUMERIC(10,2) NOT NULL CHECK (cantidad > 0),
    importe        NUMERIC(10,2) NOT NULL CHECK (importe >= 0),
    reingresa      BOOLEAN       NOT NULL DEFAULT TRUE              -- vuelve al inventario
);

-- Bitácora: permite registrar intentos de acceso de usuarios inexistentes y el origen de la petición.
ALTER TABLE bitacora ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE bitacora ADD COLUMN IF NOT EXISTS usuario_texto VARCHAR(40);
ALTER TABLE bitacora ADD COLUMN IF NOT EXISTS ip VARCHAR(45);
CREATE INDEX IF NOT EXISTS ix_bitacora_fecha ON bitacora (fecha DESC);
CREATE INDEX IF NOT EXISTS ix_bitacora_tabla ON bitacora (tabla, registro_id);

-- Avisos persistidos para Caja (el WebSocket solo notifica; Caja recupera por la API sin perder avisos).
CREATE TABLE IF NOT EXISTS aviso_caja (
    id         SERIAL PRIMARY KEY,
    tipo       VARCHAR(30)  NOT NULL,
    venta_id   INT          REFERENCES venta(id),
    mensaje    VARCHAR(300) NOT NULL,
    creado_en  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_aviso_caja_id ON aviso_caja (id);

-- Folio consecutivo para tickets de devolución.
CREATE SEQUENCE IF NOT EXISTS devolucion_folio_seq;
