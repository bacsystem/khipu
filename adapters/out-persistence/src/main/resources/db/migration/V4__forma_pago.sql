-- Forma de pago (RS 193-2020): contado por defecto; al crédito lleva monto neto pendiente y cuotas.
ALTER TABLE comprobante
    ADD COLUMN forma_pago      VARCHAR(8)    NOT NULL DEFAULT 'CONTADO',
    ADD COLUMN monto_pendiente NUMERIC(14,2);

CREATE TABLE comprobante_cuota (
    comprobante_id UUID          NOT NULL REFERENCES comprobante(documento_id),
    orden          SMALLINT      NOT NULL,
    monto          NUMERIC(14,2) NOT NULL,
    vencimiento    DATE          NOT NULL,
    PRIMARY KEY (comprobante_id, orden)
);
