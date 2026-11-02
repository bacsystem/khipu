-- #327: liquidación de compra (tipo 04, serie L###). El vendedor sin RUC va en las columnas receptor_* (documento, nombre y la dirección de su domicilio); aquí el resto de
-- su domicilio (ubigeo y condición del catálogo 60), el lugar de la operación (dirección, ubigeo y condición) y la tasa de la retención del Impuesto a la Renta. Todas nulas
-- en los demás comprobantes: retencion_renta_tasa no nula es lo que marca una liquidación de compra al leerla.
ALTER TABLE comprobante
    ADD COLUMN vendedor_ubigeo CHAR(6),
    ADD COLUMN vendedor_condicion CHAR(2),
    ADD COLUMN lugar_direccion VARCHAR(250),
    ADD COLUMN lugar_ubigeo CHAR(6),
    ADD COLUMN lugar_condicion CHAR(2),
    ADD COLUMN retencion_renta_tasa NUMERIC(8, 5);

-- Una liquidación de compra trae los seis datos y ningún otro comprobante trae alguno: lo que el dominio exige al emitir, también en la base.
-- NOT VALID: las columnas acaban de nacer y toda fila existente las tiene nulas (cumple por construcción), así que no hace falta recorrer la tabla bajo bloqueo para validarla;
-- las filas nuevas y las que se modifiquen sí se comprueban.
ALTER TABLE comprobante ADD CONSTRAINT comprobante_liquidacion_completa CHECK (
    (vendedor_ubigeo IS NULL AND vendedor_condicion IS NULL AND lugar_direccion IS NULL AND lugar_ubigeo IS NULL AND lugar_condicion IS NULL AND retencion_renta_tasa IS NULL)
    OR (vendedor_ubigeo IS NOT NULL AND vendedor_condicion IS NOT NULL AND lugar_direccion IS NOT NULL AND lugar_ubigeo IS NOT NULL AND lugar_condicion IS NOT NULL AND retencion_renta_tasa IS NOT NULL)) NOT VALID;
