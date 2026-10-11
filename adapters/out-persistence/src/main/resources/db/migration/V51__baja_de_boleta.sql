-- #293: una nota que modifica una boleta se anula, como la boleta, en el resumen diario (RC), nunca en un RA (2308/2310). El tipo de la línea (07/08)
-- ya no alcanza para saberlo: se guarda si el documento es «de boleta». Las bajas existentes son de boletas (03) o de facturas y sus notas F.
ALTER TABLE comunicacion_baja ADD COLUMN de_boleta BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE comunicacion_baja SET de_boleta = TRUE WHERE tipo_comprobante = '03';
