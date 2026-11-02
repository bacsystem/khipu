-- #20 (274-H1): el resumen diario informa boletas (estado 1, alta) además de anularlas (estado 3). La tabla de bajas guarda también esos resúmenes de alta:
-- mismo ciclo (sendSummary, ticket, getStatus, CDR) y mismo correlativo del día. Todo lo anterior es una baja, de ahí el valor por defecto; una constante
-- por defecto no reescribe la tabla.
ALTER TABLE comunicacion_baja ADD COLUMN condicion VARCHAR(5) NOT NULL DEFAULT 'BAJA';
