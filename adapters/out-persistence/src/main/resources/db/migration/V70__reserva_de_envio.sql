-- LOG-01 (auditoría 2026-10-22): reserva del envío a SUNAT. Antes de llamar a sendBill el envío marca la fila (UPDATE condicional sobre
-- FIRMADO/ERROR_ENVIO) y el guardado del resultado la libera; un segundo envío del mismo comprobante mientras tanto recibe ENVIO_EN_CURSO
-- en vez de mandar el mismo número dos veces. Columna nueva y nullable: no reescribe la tabla ni bloquea más que el ALTER instantáneo.
ALTER TABLE documento ADD COLUMN enviando_desde TIMESTAMPTZ;
