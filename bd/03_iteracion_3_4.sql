-- Iteraciones 3 y 4: cambios aditivos y auditables.
-- Este script es idempotente para instalaciones existentes.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS creado_en TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS actualizado_en TIMESTAMP NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS ix_usuario_activo_rol ON usuario (activo, rol);

ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS motivo VARCHAR(200);
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS referencia VARCHAR(80);
CREATE INDEX IF NOT EXISTS ix_movimiento_producto_fecha
  ON movimiento_inventario (producto_id, fecha DESC);
CREATE INDEX IF NOT EXISTS ix_movimiento_usuario_fecha
  ON movimiento_inventario (usuario_id, fecha DESC);

ALTER TABLE factura ADD COLUMN IF NOT EXISTS mock BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE factura ADD COLUMN IF NOT EXISTS idempotencia VARCHAR(80);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS respuesta_mock TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS ux_factura_idempotencia
  ON factura (idempotencia) WHERE idempotencia IS NOT NULL;

-- Recuperación: respalde la base antes de aplicar; las columnas/indexes pueden
-- eliminarse individualmente si se revierte la iteración.
