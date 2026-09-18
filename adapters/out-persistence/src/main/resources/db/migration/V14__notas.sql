-- Notas de crédito (07) y débito (08): comprobante que modifican (cac:BillingReference) y motivo del catálogo 09/10 (cac:DiscrepancyResponse).
ALTER TABLE comprobante
    ADD COLUMN nota_tipo_afectado   VARCHAR(2),
    ADD COLUMN nota_serie_afectada  VARCHAR(4),
    ADD COLUMN nota_numero_afectado BIGINT,
    ADD COLUMN nota_motivo          VARCHAR(2),
    ADD COLUMN nota_descripcion     VARCHAR(500);

CREATE INDEX comprobante_nota_afectado_idx ON comprobante (nota_serie_afectada, nota_numero_afectado) WHERE nota_serie_afectada IS NOT NULL;
