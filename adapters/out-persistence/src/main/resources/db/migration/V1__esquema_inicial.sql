CREATE TABLE tenant (
    id                  UUID PRIMARY KEY,
    ruc                 VARCHAR(11) NOT NULL UNIQUE,
    razon_social        VARCHAR(250) NOT NULL,
    entorno             VARCHAR(12) NOT NULL,
    sol_usuario_enc     BYTEA,
    sol_clave_enc       BYTEA,
    cert_pkcs12_enc     BYTEA,
    cert_clave_enc      BYTEA,
    cert_vigencia_hasta DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_key (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenant(id),
    key_hash   VARCHAR(64) NOT NULL UNIQUE,
    prefijo    VARCHAR(10) NOT NULL,
    activa     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

CREATE TABLE serie (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    tipo          VARCHAR(2) NOT NULL,
    codigo        VARCHAR(4) NOT NULL,
    ultimo_numero BIGINT NOT NULL DEFAULT 0,
    activa        BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (tenant_id, tipo, codigo)
);

CREATE TABLE documento (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    tipo             VARCHAR(2) NOT NULL,
    serie            VARCHAR(4) NOT NULL,
    numero           BIGINT NOT NULL,
    fecha_emision    DATE NOT NULL,
    estado           VARCHAR(25) NOT NULL,
    hash             VARCHAR(100),
    nombre_archivo   VARCHAR(40) NOT NULL,
    ticket           VARCHAR(40),
    intentos         INT NOT NULL DEFAULT 0,
    ultimo_error     TEXT,
    cdr_codigo       VARCHAR(4),
    cdr_descripcion  TEXT,
    cdr_observaciones JSONB,
    xml_key          VARCHAR(200),
    cdr_key          VARCHAR(200),
    pdf_key          VARCHAR(200),
    idempotency_key  VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, tipo, serie, numero)
);
CREATE INDEX ix_documento_tenant_estado ON documento (tenant_id, estado);
CREATE INDEX ix_documento_tenant_fecha  ON documento (tenant_id, fecha_emision);
CREATE UNIQUE INDEX ux_documento_idempotency ON documento (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE comprobante (
    documento_id       UUID PRIMARY KEY REFERENCES documento(id),
    tipo_operacion     VARCHAR(4) NOT NULL,
    moneda             VARCHAR(3) NOT NULL,
    receptor_tipo_doc  VARCHAR(1) NOT NULL,
    receptor_num_doc   VARCHAR(15) NOT NULL,
    receptor_nombre    VARCHAR(250) NOT NULL,
    receptor_direccion VARCHAR(250),
    total_gravado      NUMERIC(14,2) NOT NULL,
    total_exonerado    NUMERIC(14,2) NOT NULL,
    total_inafecto     NUMERIC(14,2) NOT NULL,
    total_igv          NUMERIC(14,2) NOT NULL,
    total              NUMERIC(14,2) NOT NULL
);

CREATE TABLE comprobante_item (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comprobante_id       UUID NOT NULL REFERENCES comprobante(documento_id),
    orden                INT NOT NULL,
    codigo               VARCHAR(30),
    descripcion          TEXT NOT NULL,
    unidad               VARCHAR(3) NOT NULL,
    cantidad             NUMERIC(14,4) NOT NULL,
    precio_unitario      NUMERIC(14,4) NOT NULL,
    tipo_afectacion_igv  VARCHAR(2) NOT NULL
);

CREATE TABLE outbox (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    agregado          VARCHAR(12) NOT NULL DEFAULT 'DOCUMENTO',
    agregado_id       UUID NOT NULL,
    accion            VARCHAR(20) NOT NULL,
    intentos          INT NOT NULL DEFAULT 0,
    siguiente_intento TIMESTAMPTZ NOT NULL,
    locked_until      TIMESTAMPTZ,
    ultimo_error      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pendientes ON outbox (siguiente_intento) WHERE locked_until IS NULL;

CREATE TABLE evento_documento (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id    UUID NOT NULL,
    estado_anterior VARCHAR(25),
    estado_nuevo    VARCHAR(25) NOT NULL,
    detalle         TEXT,
    ocurrido_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
