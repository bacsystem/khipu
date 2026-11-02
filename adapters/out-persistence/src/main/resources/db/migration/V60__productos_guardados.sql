-- #320: productos y servicios guardados por empresa (autocompletado de los ítems de «Nuevo comprobante»).
-- Un producto es una línea que la empresa factura seguido: descripción, unidad (catálogo 03), afectación del IGV (catálogo 07), precio unitario, código interno y código de producto SUNAT (catálogo 25).
-- El código interno, cuando existe, es único por empresa; la descripción no (dos productos pueden llamarse igual).
CREATE TABLE producto (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL REFERENCES tenant (id),
    codigo              VARCHAR(30),
    descripcion         VARCHAR(500)   NOT NULL,
    unidad              VARCHAR(5)     NOT NULL,
    tipo_afectacion_igv VARCHAR(2)     NOT NULL,
    precio_unitario     NUMERIC(22, 10) NOT NULL,
    codigo_sunat        VARCHAR(8),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_producto_codigo ON producto (tenant_id, codigo) WHERE codigo IS NOT NULL;
-- La búsqueda por nombre recorre los productos de la empresa, que son cientos o pocos miles.
CREATE INDEX ix_producto_tenant_descripcion ON producto (tenant_id, descripcion);
