-- Cargos del comprobante (catálogo 53, ChargeIndicator true): globales (item_orden NULL: 46/49/50) o de línea (47/48).
CREATE TABLE comprobante_cargo (
    comprobante_id UUID          NOT NULL REFERENCES comprobante(documento_id),
    item_orden     SMALLINT,
    orden          SMALLINT      NOT NULL,
    codigo         VARCHAR(2)    NOT NULL,
    tipo           VARCHAR(10)   NOT NULL,
    valor          NUMERIC(14,5) NOT NULL,
    PRIMARY KEY (comprobante_id, orden)
);
