-- Retención del IGV (catálogo 53: 62) y percepción (51/52/53, tasas del catálogo 22) informadas en la factura.
ALTER TABLE comprobante
    ADD COLUMN retencion_porcentaje NUMERIC(8,3),
    ADD COLUMN retencion_monto      NUMERIC(14,2),
    ADD COLUMN percepcion_regimen   VARCHAR(2),
    ADD COLUMN percepcion_porcentaje NUMERIC(8,3),
    ADD COLUMN percepcion_base      NUMERIC(14,2),
    ADD COLUMN percepcion_monto     NUMERIC(14,2);
