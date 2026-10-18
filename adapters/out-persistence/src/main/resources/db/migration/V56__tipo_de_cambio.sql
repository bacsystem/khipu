-- #316: tipo de cambio del comprobante (soles por unidad de su moneda), de hasta 3 enteros y 3 decimales. Opcional y solo fuera de soles; los ya emitidos quedan sin él.
ALTER TABLE comprobante ADD COLUMN tipo_cambio NUMERIC(6, 3);
