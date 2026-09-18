-- Descuentos (catálogo 53): por línea (00 afecta base / 01 no) y global (02 / 03). Se guarda lo enviado; los montos se recalculan.
ALTER TABLE comprobante_item
    ADD COLUMN descuento_tipo        VARCHAR(10),
    ADD COLUMN descuento_valor       NUMERIC(14,5),
    ADD COLUMN descuento_afecta_base BOOLEAN;

ALTER TABLE comprobante
    ADD COLUMN descuento_global_tipo        VARCHAR(10),
    ADD COLUMN descuento_global_valor       NUMERIC(14,5),
    ADD COLUMN descuento_global_afecta_base BOOLEAN;
