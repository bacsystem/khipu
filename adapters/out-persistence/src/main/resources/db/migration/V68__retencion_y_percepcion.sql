-- #40: comprobantes de retención (20, serie R###) y de percepción (40, serie P###) de un agente designado por SUNAT.

-- La designación de la empresa como agente: la registra el administrador de la plataforma (acción DESIGNAR_AGENTE_EMPRESA). Por defecto, ninguna.
ALTER TABLE tenant
    ADD COLUMN agente_retencion BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN agente_percepcion BOOLEAN NOT NULL DEFAULT false;

-- La contraparte (el proveedor al que se retiene o el cliente al que se percibe) va en las columnas receptor_*; el total, en total (lo retenido o percibido, en soles), y el
-- redondeo del total pagado o cobrado, en redondeo. Aquí el régimen (catálogo 23 o 22) y su tasa: agente_regimen no nulo es lo que marca un 20 o un 40 al leerlo. No tienen tipo
-- de operación (catálogo 51): la columna pasa a admitir nulo solo para ellos.
ALTER TABLE comprobante
    ADD COLUMN agente_regimen CHAR(2),
    ADD COLUMN agente_tasa NUMERIC(5, 2),
    ALTER COLUMN tipo_operacion DROP NOT NULL;

-- NOT VALID: las columnas nuevas nacen nulas y toda fila existente tiene tipo de operación (era NOT NULL), así que cumplen por construcción y no hace falta recorrer la tabla bajo
-- bloqueo para validarlas; las filas nuevas y las que se modifiquen sí se comprueban.
ALTER TABLE comprobante ADD CONSTRAINT comprobante_agente_completo CHECK ((agente_regimen IS NULL) = (agente_tasa IS NULL)) NOT VALID;
ALTER TABLE comprobante ADD CONSTRAINT comprobante_tipo_operacion_salvo_agente CHECK (tipo_operacion IS NOT NULL OR agente_regimen IS NOT NULL) NOT VALID;

-- Los comprobantes que el agente paga (retención) o cobra (percepción), cada uno con su pago, en el orden del XML. Importes en la moneda del documento; tipo de cambio y su fecha
-- solo fuera de soles, los dos juntos. Lo retenido o percibido y el neto no se guardan: salen del pago, el tipo de cambio y la tasa, igual que los totales de las líneas.
CREATE TABLE comprobante_documento_agente (
    comprobante_id    UUID          NOT NULL REFERENCES comprobante (documento_id),
    orden             INT           NOT NULL,
    tipo              CHAR(2)       NOT NULL,
    numero            VARCHAR(41)   NOT NULL,
    fecha_emision     DATE          NOT NULL,
    importe_total     NUMERIC(14, 2) NOT NULL,
    moneda            CHAR(3)       NOT NULL,
    numero_pago       BIGINT        NOT NULL,
    fecha_pago        DATE          NOT NULL,
    importe_pago      NUMERIC(14, 2) NOT NULL,
    tipo_cambio       NUMERIC(10, 6),
    fecha_tipo_cambio DATE,
    PRIMARY KEY (comprobante_id, orden),
    CONSTRAINT comprobante_documento_agente_tipo_de_cambio CHECK ((tipo_cambio IS NULL) = (fecha_tipo_cambio IS NULL))
);
