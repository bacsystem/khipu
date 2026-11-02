-- Padrón reducido de SUNAT (#361): quién existe como contribuyente, en qué estado y dónde vive. Es dato público y de toda la plataforma, no de una empresa: no lleva
-- tenant_id. Son ~18 millones de filas (15,5 M de RUC 10, 2,4 M de RUC 20), todas con su estado: la validación del comprador necesita las bajas, no solo los activos.
CREATE TABLE padron_ruc (
    ruc          VARCHAR(11) PRIMARY KEY,
    razon_social TEXT NOT NULL,
    -- Tal como los publica SUNAT (el archivo los trunca a 20 caracteres: «BAJA PROV. POR OFICI»).
    estado       TEXT NOT NULL,
    condicion    TEXT NOT NULL,
    ubigeo       VARCHAR(6),
    direccion    TEXT
);

-- Filas recién cargadas, antes de mezclarlas con padron_ruc. UNLOGGED: no escribe WAL (se vuelve a cargar entera si la base se cae) y se vacía al terminar cada carga.
CREATE UNLOGGED TABLE padron_ruc_carga (
    ruc          VARCHAR(11) PRIMARY KEY,
    razon_social TEXT NOT NULL,
    estado       TEXT NOT NULL,
    condicion    TEXT NOT NULL,
    ubigeo       VARCHAR(6),
    direccion    TEXT
);

-- Qué se cargó y cuándo. La consulta lee la última EXITOSA para decir «padrón al día X» y para no afirmar «no existe» cuando el padrón nunca se cargó.
CREATE TABLE carga_padron_ruc (
    id          BIGSERIAL PRIMARY KEY,
    iniciada    TIMESTAMPTZ NOT NULL,
    terminada   TIMESTAMPTZ NOT NULL,
    resultado   TEXT NOT NULL CHECK (resultado IN ('EXITOSA', 'FALLIDA')),
    origen      TEXT NOT NULL,
    filas       BIGINT NOT NULL DEFAULT 0,
    descartadas BIGINT NOT NULL DEFAULT 0,
    nuevas      BIGINT NOT NULL DEFAULT 0,
    cambiadas   BIGINT NOT NULL DEFAULT 0,
    borradas    BIGINT NOT NULL DEFAULT 0,
    error       TEXT
);
CREATE INDEX ix_carga_padron_ruc_exitosa ON carga_padron_ruc (terminada DESC) WHERE resultado = 'EXITOSA';
