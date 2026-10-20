-- #39: guías de remisión electrónicas, remitente (09) y transportista (31). Tablas nuevas: no se toca ninguna tabla viva (la serie T###/V### vive en
-- `serie`, que no tiene restricción sobre el tipo). Al ser nuevas, sus CHECK se validan al crearse (no hay filas que revisar).
CREATE TABLE guia_remision (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL REFERENCES tenant(id),
    tipo                       VARCHAR(2) NOT NULL,
    serie                      VARCHAR(4) NOT NULL,
    numero                     BIGINT NOT NULL,
    fecha_emision              DATE NOT NULL,
    hora_emision               TIME NOT NULL,
    estado                     VARCHAR(30) NOT NULL,
    destinatario_tipo_doc      VARCHAR(1) NOT NULL,
    destinatario_num_doc       VARCHAR(15) NOT NULL,
    destinatario_nombre        VARCHAR(250) NOT NULL,
    -- Guía remitente (09): motivo (catálogo 20), modalidad (catálogo 18) y, en transporte público, la empresa de transporte.
    motivo                     VARCHAR(2),
    descripcion_motivo         VARCHAR(100),
    modalidad                  VARCHAR(2),
    transportista_ruc          VARCHAR(11),
    transportista_razon_social VARCHAR(250),
    transportista_mtc          VARCHAR(20),
    -- Guía transportista (31): el remitente de los bienes y el registro MTC del transportista que la emite.
    remitente_tipo_doc         VARCHAR(1),
    remitente_num_doc          VARCHAR(15),
    remitente_nombre           VARCHAR(250),
    registro_mtc               VARCHAR(20),
    fecha_inicio_traslado      DATE NOT NULL,
    peso_bruto                 NUMERIC(15, 3) NOT NULL,
    unidad_peso                VARCHAR(3) NOT NULL,
    partida_ubigeo             VARCHAR(6) NOT NULL,
    partida_direccion          VARCHAR(500) NOT NULL,
    partida_establecimiento    VARCHAR(4),
    partida_ruc                VARCHAR(11),
    llegada_ubigeo             VARCHAR(6) NOT NULL,
    llegada_direccion          VARCHAR(500) NOT NULL,
    llegada_establecimiento    VARCHAR(4),
    llegada_ruc                VARCHAR(11),
    indicadores                VARCHAR(250),
    observaciones              VARCHAR(250),
    nombre_archivo             VARCHAR(40) NOT NULL,
    hash                       VARCHAR(100),
    xml_key                    VARCHAR(250),
    cdr_key                    VARCHAR(250),
    ticket                     VARCHAR(40),
    fecha_recepcion            TIMESTAMP,
    intentos                   INT NOT NULL DEFAULT 0,
    consultas_ticket           INT NOT NULL DEFAULT 0,
    reintento_automatico       BOOLEAN NOT NULL DEFAULT false,
    ultimo_error               TEXT,
    cdr_codigo                 VARCHAR(10),
    cdr_descripcion            TEXT,
    cdr_observaciones          JSONB,
    enlace_consulta            VARCHAR(500),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, tipo, serie, numero),
    CONSTRAINT guia_remision_tipo CHECK (tipo IN ('09', '31')),
    CONSTRAINT guia_remision_serie_del_tipo CHECK ((tipo = '09' AND serie ~ '^T[A-Z0-9]{3}$') OR (tipo = '31' AND serie ~ '^V[A-Z0-9]{3}$')),
    -- Un solo origen de verdad: el tipo y los datos de ese tipo van juntos (una 09 sin motivo, o una 31 sin remitente, no se pueden guardar).
    CONSTRAINT guia_remision_datos_del_tipo CHECK (
        (tipo = '09' AND motivo IS NOT NULL AND descripcion_motivo IS NOT NULL AND modalidad IN ('01', '02')
            AND remitente_tipo_doc IS NULL AND remitente_num_doc IS NULL AND remitente_nombre IS NULL AND registro_mtc IS NULL)
        OR (tipo = '31' AND motivo IS NULL AND descripcion_motivo IS NULL AND modalidad IS NULL AND transportista_ruc IS NULL
            AND remitente_tipo_doc IS NOT NULL AND remitente_num_doc IS NOT NULL AND remitente_nombre IS NOT NULL AND registro_mtc IS NOT NULL)),
    -- La empresa de transporte es de la guía remitente en transporte público (2534) y en privado no va (3347).
    CONSTRAINT guia_remision_transportista_publico CHECK ((modalidad = '01') = (transportista_ruc IS NOT NULL) OR tipo = '31'),
    CONSTRAINT guia_remision_estado CHECK (estado IN ('FIRMADO', 'ERROR_ENVIO', 'ENVIADO_PENDIENTE_TICKET', 'ACEPTADO', 'ACEPTADO_CON_OBS', 'RECHAZADO')),
    CONSTRAINT guia_remision_ticket CHECK (estado <> 'ENVIADO_PENDIENTE_TICKET' OR ticket IS NOT NULL),
    CONSTRAINT guia_remision_peso CHECK (peso_bruto > 0 AND unidad_peso IN ('KGM', 'TNE'))
);
CREATE INDEX ix_guia_remision_tenant_fecha ON guia_remision (tenant_id, fecha_emision DESC, numero DESC);

-- Bienes que se trasladan, en orden.
CREATE TABLE guia_remision_item (
    guia_id      UUID NOT NULL REFERENCES guia_remision(id) ON DELETE CASCADE,
    orden        INT NOT NULL,
    codigo       VARCHAR(30),
    descripcion  VARCHAR(500) NOT NULL,
    unidad       VARCHAR(3) NOT NULL,
    cantidad     NUMERIC(22, 10) NOT NULL CHECK (cantidad > 0),
    codigo_sunat VARCHAR(8),
    PRIMARY KEY (guia_id, orden)
);

-- Transporte: vehículos y conductores, el principal en el orden 0 y hasta dos secundarios (4389, 4376).
CREATE TABLE guia_remision_vehiculo (
    guia_id UUID NOT NULL REFERENCES guia_remision(id) ON DELETE CASCADE,
    orden   INT NOT NULL CHECK (orden BETWEEN 0 AND 2),
    placa   VARCHAR(8) NOT NULL,
    PRIMARY KEY (guia_id, orden)
);
CREATE TABLE guia_remision_conductor (
    guia_id   UUID NOT NULL REFERENCES guia_remision(id) ON DELETE CASCADE,
    orden     INT NOT NULL CHECK (orden BETWEEN 0 AND 2),
    tipo_doc  VARCHAR(1) NOT NULL,
    num_doc   VARCHAR(15) NOT NULL,
    nombres   VARCHAR(250) NOT NULL,
    apellidos VARCHAR(250) NOT NULL,
    licencia  VARCHAR(10) NOT NULL,
    PRIMARY KEY (guia_id, orden)
);

-- Documentos relacionados con el traslado (catálogo 61).
CREATE TABLE guia_remision_documento (
    guia_id    UUID NOT NULL REFERENCES guia_remision(id) ON DELETE CASCADE,
    orden      INT NOT NULL,
    tipo       VARCHAR(2) NOT NULL,
    numero     VARCHAR(100) NOT NULL,
    ruc_emisor VARCHAR(11),
    PRIMARY KEY (guia_id, orden)
);
