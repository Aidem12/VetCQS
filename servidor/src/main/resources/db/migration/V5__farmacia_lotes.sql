-- Farmacia e inventario avanzado: proveedores, compras y lotes con caducidad (cambios aditivos).

CREATE TABLE IF NOT EXISTS proveedor (
    id         SERIAL PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL UNIQUE,
    rfc        VARCHAR(13),
    contacto   VARCHAR(120),
    telefono   VARCHAR(20),
    correo     VARCHAR(120),
    direccion  VARCHAR(200),
    notas      VARCHAR(500),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS compra (
    id                 SERIAL PRIMARY KEY,
    proveedor_id       INT           NOT NULL REFERENCES proveedor(id),
    documento          VARCHAR(60),                       -- factura o remisión del proveedor
    fecha              TIMESTAMP     NOT NULL DEFAULT now(),
    usuario_id         INT           NOT NULL REFERENCES usuario(id),
    total              NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    notas              VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS ix_compra_proveedor ON compra (proveedor_id, fecha DESC);

-- La tabla lote ya existía en el modelo original; se completa sin alterar sus datos.
-- "cantidad" sigue siendo la cantidad disponible del lote.
ALTER TABLE lote ADD COLUMN IF NOT EXISTS cantidad_recibida NUMERIC(10,2);
ALTER TABLE lote ADD COLUMN IF NOT EXISTS costo_unitario NUMERIC(10,2) CHECK (costo_unitario >= 0);
ALTER TABLE lote ADD COLUMN IF NOT EXISTS proveedor_id INT REFERENCES proveedor(id);
ALTER TABLE lote ADD COLUMN IF NOT EXISTS compra_id INT REFERENCES compra(id);
ALTER TABLE lote ADD COLUMN IF NOT EXISTS fecha_recepcion TIMESTAMP NOT NULL DEFAULT now();
UPDATE lote SET cantidad_recibida = cantidad WHERE cantidad_recibida IS NULL;
CREATE INDEX IF NOT EXISTS ix_lote_producto_caducidad ON lote (producto_id, caducidad, id);

CREATE TABLE IF NOT EXISTS compra_detalle (
    id              SERIAL PRIMARY KEY,
    compra_id       INT           NOT NULL REFERENCES compra(id),
    producto_id     INT           NOT NULL REFERENCES producto(id),
    lote_id         INT           REFERENCES lote(id),
    cantidad        NUMERIC(10,2) NOT NULL CHECK (cantidad > 0),
    costo_unitario  NUMERIC(10,2) NOT NULL CHECK (costo_unitario >= 0)
);

-- Último costo de adquisición del producto, para márgenes cuando no hay lotes.
ALTER TABLE producto ADD COLUMN IF NOT EXISTS costo_ultimo NUMERIC(10,2) CHECK (costo_ultimo >= 0);

-- El kardex registra la venta asociada y el tipo DEVOLUCION (reembolsos con reversión de inventario).
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS venta_id INT REFERENCES venta(id);
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT con.conname FROM pg_constraint con
             JOIN pg_class cl ON cl.oid = con.conrelid
             JOIN pg_namespace ns ON ns.oid = cl.relnamespace
             WHERE cl.relname = 'movimiento_inventario' AND ns.nspname = current_schema() AND con.contype = 'c'
               AND pg_get_constraintdef(con.oid) LIKE '%tipo%'
    LOOP
        EXECUTE format('ALTER TABLE movimiento_inventario DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE movimiento_inventario ADD CONSTRAINT ck_movimiento_tipo
    CHECK (tipo IN ('ENTRADA', 'VENTA', 'AJUSTE', 'CANCELACION', 'DEVOLUCION'));
