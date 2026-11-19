-- Agenda veterinaria de citas (cambios aditivos).
-- La extensión btree_gist permite que PostgreSQL impida citas superpuestas del mismo
-- veterinario aun cuando dos personas reservan al mismo tiempo.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS tipo_cita (
    id            SERIAL PRIMARY KEY,
    nombre        VARCHAR(80)  NOT NULL UNIQUE,
    duracion_min  INT          NOT NULL CHECK (duracion_min BETWEEN 5 AND 480),
    color         VARCHAR(7)   NOT NULL DEFAULT '#2F7DC1',
    producto_id   INT          REFERENCES producto(id),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Bloques de atención por día (1 = lunes ... 7 = domingo). Varios bloques en el mismo día
-- representan descansos (por ejemplo 09:00-14:00 y 16:00-19:00).
CREATE TABLE IF NOT EXISTS horario_veterinario (
    id              SERIAL PRIMARY KEY,
    veterinario_id  INT      NOT NULL REFERENCES usuario(id),
    dia_semana      SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio     TIME     NOT NULL,
    hora_fin        TIME     NOT NULL,
    CHECK (hora_fin > hora_inicio)
);
CREATE INDEX IF NOT EXISTS ix_horario_vet_dia ON horario_veterinario (veterinario_id, dia_semana);

CREATE TABLE IF NOT EXISTS ausencia_veterinario (
    id              SERIAL PRIMARY KEY,
    veterinario_id  INT          NOT NULL REFERENCES usuario(id),
    tipo            VARCHAR(12)  NOT NULL CHECK (tipo IN ('VACACIONES', 'AUSENCIA', 'DESCANSO')),
    inicio          TIMESTAMP    NOT NULL,
    fin             TIMESTAMP    NOT NULL,
    motivo          VARCHAR(200),
    creado_por      INT          NOT NULL REFERENCES usuario(id),
    creado_en       TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (fin > inicio)
);
CREATE INDEX IF NOT EXISTS ix_ausencia_vet ON ausencia_veterinario (veterinario_id, inicio);

CREATE TABLE IF NOT EXISTS cita (
    id                  SERIAL PRIMARY KEY,
    mascota_id          INT          NOT NULL REFERENCES mascota(id),
    veterinario_id      INT          NOT NULL REFERENCES usuario(id),
    tipo_cita_id        INT          NOT NULL REFERENCES tipo_cita(id),
    inicio              TIMESTAMP    NOT NULL,
    fin                 TIMESTAMP    NOT NULL,
    estado              VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO')),
    motivo              VARCHAR(250),
    notas               TEXT,
    consulta_id         INT          UNIQUE REFERENCES consulta(id),
    motivo_cancelacion  VARCHAR(200),
    creado_por          INT          NOT NULL REFERENCES usuario(id),
    creado_en           TIMESTAMP    NOT NULL DEFAULT now(),
    actualizado_por     INT          REFERENCES usuario(id),
    actualizado_en      TIMESTAMP,
    CHECK (fin > inicio),
    CONSTRAINT ex_cita_sin_traslape EXCLUDE USING gist (
        veterinario_id WITH =,
        tsrange(inicio, fin) WITH &&
    ) WHERE (estado NOT IN ('CANCELADA', 'NO_ASISTIO'))
);
CREATE INDEX IF NOT EXISTS ix_cita_inicio ON cita (inicio);
CREATE INDEX IF NOT EXISTS ix_cita_mascota ON cita (mascota_id, inicio DESC);

-- Autoría y cambios de cada cita.
CREATE TABLE IF NOT EXISTS cita_historial (
    id          SERIAL PRIMARY KEY,
    cita_id     INT          NOT NULL REFERENCES cita(id),
    usuario_id  INT          NOT NULL REFERENCES usuario(id),
    accion      VARCHAR(20)  NOT NULL,
    detalle     VARCHAR(400),
    fecha       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_cita_historial ON cita_historial (cita_id, fecha);

INSERT INTO tipo_cita (nombre, duracion_min, color)
VALUES ('Consulta general', 30, '#2F7DC1'),
       ('Vacunación', 15, '#21865B'),
       ('Revisión de seguimiento', 20, '#8A5CC2'),
       ('Cirugía menor', 90, '#C0392B')
ON CONFLICT (nombre) DO NOTHING;
