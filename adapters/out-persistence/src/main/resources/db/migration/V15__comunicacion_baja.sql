-- Comunicación de baja (VoidedDocuments, RA-yyyymmdd-N): una por comprobante, correlativo por empresa y día.
CREATE TABLE comunicacion_baja (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenant(id),
    fecha_generacion  DATE          NOT NULL,
    correlativo       INTEGER       NOT NULL,
    comprobante_id    UUID          NOT NULL REFERENCES documento(id),
    tipo_comprobante  VARCHAR(2)    NOT NULL,
    serie             VARCHAR(4)    NOT NULL,
    numero            BIGINT        NOT NULL,
    fecha_referencia  DATE          NOT NULL,
    motivo            VARCHAR(100)  NOT NULL,
    estado            VARCHAR(20)   NOT NULL,
    ticket            VARCHAR(40),
    xml_key           TEXT,
    cdr_key           TEXT,
    cdr_codigo        VARCHAR(10),
    cdr_descripcion   TEXT,
    intentos          INTEGER       NOT NULL DEFAULT 0,
    ultimo_error      TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, fecha_generacion, correlativo)
);
CREATE INDEX comunicacion_baja_comprobante_idx ON comunicacion_baja (comprobante_id);
