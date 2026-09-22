-- Establecimientos anexos (#80): sucursales declaradas en el RUC, con su domicilio; el 0000 sigue siendo el domicilio del tenant.
CREATE TABLE establecimiento (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    codigo           VARCHAR(4) NOT NULL,
    nombre           VARCHAR(100) NOT NULL,
    dom_ubigeo       VARCHAR(6) NOT NULL,
    dom_direccion    VARCHAR(200) NOT NULL,
    dom_urbanizacion VARCHAR(25),
    dom_distrito     VARCHAR(30),
    dom_provincia    VARCHAR(30),
    dom_departamento VARCHAR(30),
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, codigo)
);
-- Cada serie emite desde un establecimiento; las existentes quedan en el domicilio fiscal.
ALTER TABLE serie ADD COLUMN establecimiento VARCHAR(4) NOT NULL DEFAULT '0000';
