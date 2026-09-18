-- Campos opcionales de la factura (hoja Factura2_0): nombre comercial del emisor (11), fecha de vencimiento (8),
-- redondeo del importe total (56) y, por ítem, código de producto SUNAT (28) y GTIN (29).
ALTER TABLE tenant ADD COLUMN nombre_comercial VARCHAR(1500);

ALTER TABLE comprobante
    ADD COLUMN fecha_vencimiento DATE,
    ADD COLUMN redondeo          NUMERIC(3,2);

ALTER TABLE comprobante_item
    ADD COLUMN codigo_sunat VARCHAR(8),
    ADD COLUMN gtin_tipo    VARCHAR(7),
    ADD COLUMN gtin         VARCHAR(14);
