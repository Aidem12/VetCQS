-- Expediente clínico completo (cambios aditivos; no se borra ni modifica información existente).

-- Datos ampliados del propietario y la mascota.
ALTER TABLE duenio  ADD COLUMN IF NOT EXISTS telefono_alterno VARCHAR(20);
ALTER TABLE duenio  ADD COLUMN IF NOT EXISTS notas VARCHAR(500);
ALTER TABLE mascota ADD COLUMN IF NOT EXISTS identificacion VARCHAR(40);       -- microchip, tatuaje o placa
ALTER TABLE mascota ADD COLUMN IF NOT EXISTS color VARCHAR(40);
ALTER TABLE mascota ADD COLUMN IF NOT EXISTS esterilizado BOOLEAN;
ALTER TABLE mascota ADD COLUMN IF NOT EXISTS observaciones TEXT;
CREATE INDEX IF NOT EXISTS ix_mascota_nombre ON mascota (lower(nombre));

-- Signos vitales adicionales y evolución en la consulta.
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS frecuencia_cardiaca INT CHECK (frecuencia_cardiaca BETWEEN 10 AND 400);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS frecuencia_respiratoria INT CHECK (frecuencia_respiratoria BETWEEN 4 AND 200);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS condicion_corporal SMALLINT CHECK (condicion_corporal BETWEEN 1 AND 9);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS evolucion TEXT;

-- Vacunación, desparasitación y tratamientos preventivos.
-- Nunca se borran: un registro equivocado se anula con motivo y responsable.
CREATE TABLE IF NOT EXISTS vacuna (
    id                SERIAL PRIMARY KEY,
    mascota_id        INT          NOT NULL REFERENCES mascota(id),
    vacuna            VARCHAR(120) NOT NULL,
    lote              VARCHAR(40),
    dosis             VARCHAR(40),
    fecha_aplicacion  DATE         NOT NULL,
    proximo_refuerzo  DATE,
    veterinario_id    INT          NOT NULL REFERENCES usuario(id),
    observaciones     VARCHAR(500),
    creado_en         TIMESTAMP    NOT NULL DEFAULT now(),
    anulada           BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_anulacion  VARCHAR(200),
    anulada_por       INT          REFERENCES usuario(id),
    CHECK (proximo_refuerzo IS NULL OR proximo_refuerzo >= fecha_aplicacion),
    CHECK (NOT anulada OR (motivo_anulacion IS NOT NULL AND anulada_por IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_vacuna_mascota ON vacuna (mascota_id, fecha_aplicacion DESC);

CREATE TABLE IF NOT EXISTS tratamiento_preventivo (
    id                  SERIAL PRIMARY KEY,
    mascota_id          INT          NOT NULL REFERENCES mascota(id),
    tipo                VARCHAR(30)  NOT NULL CHECK (tipo IN ('DESPARASITACION_INTERNA', 'DESPARASITACION_EXTERNA', 'OTRO')),
    producto            VARCHAR(120) NOT NULL,
    dosis               VARCHAR(60),
    fecha_aplicacion    DATE         NOT NULL,
    proxima_aplicacion  DATE,
    veterinario_id      INT          NOT NULL REFERENCES usuario(id),
    observaciones       VARCHAR(500),
    creado_en           TIMESTAMP    NOT NULL DEFAULT now(),
    anulada             BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_anulacion    VARCHAR(200),
    anulada_por         INT          REFERENCES usuario(id),
    CHECK (proxima_aplicacion IS NULL OR proxima_aplicacion >= fecha_aplicacion),
    CHECK (NOT anulada OR (motivo_anulacion IS NOT NULL AND anulada_por IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_preventivo_mascota ON tratamiento_preventivo (mascota_id, fecha_aplicacion DESC);

-- Alergias, enfermedades preexistentes, antecedentes y alertas clínicas.
CREATE TABLE IF NOT EXISTS antecedente_clinico (
    id              SERIAL PRIMARY KEY,
    mascota_id      INT          NOT NULL REFERENCES mascota(id),
    tipo            VARCHAR(20)  NOT NULL CHECK (tipo IN ('ALERGIA', 'ENFERMEDAD', 'CIRUGIA', 'ANTECEDENTE', 'ALERTA')),
    descripcion     VARCHAR(300) NOT NULL,
    severidad       VARCHAR(10)  CHECK (severidad IN ('LEVE', 'MODERADA', 'GRAVE')),
    vigente         BOOLEAN      NOT NULL DEFAULT TRUE,
    registrado_por  INT          NOT NULL REFERENCES usuario(id),
    creado_en       TIMESTAMP    NOT NULL DEFAULT now(),
    cerrado_por     INT          REFERENCES usuario(id),
    cerrado_en      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_antecedente_mascota ON antecedente_clinico (mascota_id, vigente);

-- Notas de evolución: las consultas no se editan; las aclaraciones se agregan con autor y fecha.
CREATE TABLE IF NOT EXISTS nota_clinica (
    id           SERIAL PRIMARY KEY,
    consulta_id  INT        NOT NULL REFERENCES consulta(id),
    autor_id     INT        NOT NULL REFERENCES usuario(id),
    texto        TEXT       NOT NULL,
    creado_en    TIMESTAMP  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_nota_consulta ON nota_clinica (consulta_id, creado_en);

-- Recetas veterinarias.
CREATE TABLE IF NOT EXISTS receta (
    id                      SERIAL PRIMARY KEY,
    folio                   VARCHAR(20)  UNIQUE,
    mascota_id              INT          NOT NULL REFERENCES mascota(id),
    consulta_id             INT          REFERENCES consulta(id),
    veterinario_id          INT          NOT NULL REFERENCES usuario(id),
    fecha                   TIMESTAMP    NOT NULL DEFAULT now(),
    indicaciones_generales  TEXT
);
CREATE INDEX IF NOT EXISTS ix_receta_mascota ON receta (mascota_id, fecha DESC);

CREATE TABLE IF NOT EXISTS receta_detalle (
    id            SERIAL PRIMARY KEY,
    receta_id     INT          NOT NULL REFERENCES receta(id),
    producto_id   INT          REFERENCES producto(id),
    medicamento   VARCHAR(160) NOT NULL,
    dosis         VARCHAR(80)  NOT NULL,
    frecuencia    VARCHAR(80)  NOT NULL,
    via           VARCHAR(40)  NOT NULL,
    duracion      VARCHAR(60)  NOT NULL,
    indicaciones  VARCHAR(400)
);

-- Estudios de laboratorio y documentos adjuntos (guardados en la base, con huella SHA-256).
CREATE TABLE IF NOT EXISTS estudio (
    id              SERIAL PRIMARY KEY,
    mascota_id      INT          NOT NULL REFERENCES mascota(id),
    consulta_id     INT          REFERENCES consulta(id),
    tipo            VARCHAR(80)  NOT NULL,
    descripcion     VARCHAR(300),
    resultado       TEXT,
    fecha           DATE         NOT NULL,
    registrado_por  INT          NOT NULL REFERENCES usuario(id),
    creado_en       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_estudio_mascota ON estudio (mascota_id, fecha DESC);

CREATE TABLE IF NOT EXISTS documento_clinico (
    id               SERIAL PRIMARY KEY,
    mascota_id       INT          NOT NULL REFERENCES mascota(id),
    estudio_id       INT          REFERENCES estudio(id),
    nombre_original  VARCHAR(200) NOT NULL,
    tipo_mime        VARCHAR(60)  NOT NULL CHECK (tipo_mime IN ('application/pdf', 'image/png', 'image/jpeg')),
    tamano_bytes     INT          NOT NULL CHECK (tamano_bytes BETWEEN 1 AND 5242880),
    sha256           VARCHAR(64)  NOT NULL,
    contenido        BYTEA        NOT NULL,
    subido_por       INT          NOT NULL REFERENCES usuario(id),
    creado_en        TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_documento_mascota ON documento_clinico (mascota_id, creado_en DESC);

CREATE SEQUENCE IF NOT EXISTS receta_folio_seq;
