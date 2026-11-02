-- Administrador de la plataforma (#175): tabla propia, sin FK a cuenta/tenant — no pertenece a ningún cliente.
CREATE TABLE administrador (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    activo        BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
