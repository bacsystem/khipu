-- Factura de exportación (#65): país del receptor (catálogo 04, ISO 3166-1), Incoterm y país de uso del servicio (0201/0208).
ALTER TABLE comprobante ADD COLUMN receptor_pais CHAR(2);
ALTER TABLE comprobante ADD COLUMN incoterm VARCHAR(3);
ALTER TABLE comprobante ADD COLUMN pais_uso CHAR(2);
