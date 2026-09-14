CREATE TABLE cuenta (
    id         UUID PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL,
    email      VARCHAR(254) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id            UUID PRIMARY KEY,
    cuenta_id     UUID NOT NULL REFERENCES cuenta(id),
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    rol           VARCHAR(10) NOT NULL,
    activo        BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sesion (
    id           UUID PRIMARY KEY,
    usuario_id   UUID NOT NULL REFERENCES usuario(id),
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en    TIMESTAMPTZ NOT NULL,
    revocada     BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sesion_usuario ON sesion (usuario_id);

CREATE TABLE token_recuperacion (
    token_hash VARCHAR(64) PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    expira_en  TIMESTAMPTZ NOT NULL,
    usado      BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE tenant ADD COLUMN cuenta_id UUID REFERENCES cuenta(id);
CREATE INDEX ix_tenant_cuenta ON tenant (cuenta_id);
