-- Anticipos regularizados en la factura: referencia a la factura de anticipo y el valor sin IGV descontado (catálogo 53: 04/05/06).
CREATE TABLE comprobante_anticipo (
    comprobante_id UUID          NOT NULL REFERENCES comprobante(documento_id),
    orden          SMALLINT      NOT NULL,
    serie          VARCHAR(4)    NOT NULL,
    numero         BIGINT        NOT NULL,
    monto          NUMERIC(14,2) NOT NULL,
    afectacion     VARCHAR(10)   NOT NULL,
    fecha_pago     DATE,
    PRIMARY KEY (comprobante_id, orden)
);
