-- #317: otros tributos (9999) por línea (una tasa sobre el valor de venta) y placa del vehículo (propiedad 7000 del catálogo 55).
-- Ambas son opcionales: las líneas ya emitidas quedan sin ellas.
ALTER TABLE comprobante_item
    ADD COLUMN otros_tributos_tasa NUMERIC(8, 5),
    ADD COLUMN placa VARCHAR(8);
