-- ISC sistema 03 (#68): precio de venta al público sugerido unitario, base del ISC de la línea.
ALTER TABLE comprobante_item ADD COLUMN isc_base_pvp NUMERIC(14,5);
