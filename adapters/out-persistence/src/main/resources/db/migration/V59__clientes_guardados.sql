-- #319: clientes guardados por empresa (autocompletado de «Nuevo comprobante»).
-- Un cliente es el comprador al que la empresa le factura seguido: su documento (catálogo 06), nombre, dirección, país y correo. La clave natural es (empresa, tipo, número de documento).
CREATE TABLE cliente (
    id            UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL REFERENCES tenant (id),
    tipo_doc      VARCHAR(2)    NOT NULL,
    num_doc       VARCHAR(20)   NOT NULL,
    razon_social  VARCHAR(1500) NOT NULL,
    direccion     VARCHAR(300),
    pais          VARCHAR(2),
    email         VARCHAR(150),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_cliente_documento UNIQUE (tenant_id, tipo_doc, num_doc)
);
-- La búsqueda por documento (empieza por) la cubre la clave única; la de nombre recorre los clientes de la empresa, que son cientos o pocos miles.
CREATE INDEX ix_cliente_tenant_nombre ON cliente (tenant_id, razon_social);
