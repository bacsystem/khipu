-- Orden de compra (cac:OrderReference) y documentos relacionados: guías de remisión (catálogo 01) y otros (catálogo 12).
ALTER TABLE comprobante ADD COLUMN orden_compra VARCHAR(20);

CREATE TABLE comprobante_documento_relacionado (
    comprobante_id UUID        NOT NULL REFERENCES comprobante(documento_id),
    orden          SMALLINT    NOT NULL,
    clase          VARCHAR(5)  NOT NULL CHECK (clase IN ('GUIA', 'OTRO')),   -- GUIA (cac:DespatchDocumentReference) u OTRO (cac:AdditionalDocumentReference)
    tipo           VARCHAR(2)  NOT NULL,
    numero         VARCHAR(30) NOT NULL,
    PRIMARY KEY (comprobante_id, orden)
);
