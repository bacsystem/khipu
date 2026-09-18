-- ISC por ítem (tributo 2000: sistema del catálogo 08, tasa o monto fijo) e ICBPER (tributo 7152, una bolsa por unidad).
ALTER TABLE comprobante_item
    ADD COLUMN isc_sistema        VARCHAR(2),
    ADD COLUMN isc_tasa           NUMERIC(8,5),
    ADD COLUMN isc_monto_unitario NUMERIC(14,5),
    ADD COLUMN icbper             BOOLEAN NOT NULL DEFAULT FALSE;
