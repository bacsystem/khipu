-- Detracción (SPOT): bien/servicio del catálogo 54, porcentaje, monto en PEN, cuenta del Banco de la Nación y medio de pago (catálogo 59).
ALTER TABLE comprobante
    ADD COLUMN detraccion_codigo     VARCHAR(3),
    ADD COLUMN detraccion_porcentaje NUMERIC(8,5),
    ADD COLUMN detraccion_monto      NUMERIC(14,2),
    ADD COLUMN detraccion_cuenta     VARCHAR(100),
    ADD COLUMN detraccion_medio_pago VARCHAR(3);
