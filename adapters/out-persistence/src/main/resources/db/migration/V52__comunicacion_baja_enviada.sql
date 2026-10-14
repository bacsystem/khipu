-- El monitor global (#195, #362 B14) pregunta cada 30 segundos cuántos resúmenes diarios y comunicaciones de baja siguen ENVIADOS (con ticket, sin respuesta de SUNAT) y
-- desde cuándo espera el más antiguo. Se mide desde que SUNAT dio el ticket, no desde que se creó la fila: un resumen que estuvo en ERROR_ENVIO horas (SUNAT caída) y recién
-- entonces se envió no lleva horas esperando respuesta. `updated_at` tampoco sirve: se pisa en cada guardado, también en cada reconsulta del ticket.
ALTER TABLE comunicacion_baja ADD COLUMN enviada_en TIMESTAMPTZ;
-- Los que hoy están ENVIADOS: lo mejor que se sabe es cuándo se crearon (el envío suele ser casi inmediato).
UPDATE comunicacion_baja SET enviada_en = created_at WHERE estado = 'ENVIADA';
-- `comunicacion_baja` crece con cada boleta informada o anulada y los ENVIADOS son casi siempre cero o pocos: un índice parcial hace la lectura instantánea sin cargar las
-- escrituras (solo entran las filas mientras están ENVIADAS). Es un índice normal (no CONCURRENTLY: Flyway corre cada migración en una transacción), que bloquea las
-- escrituras de la tabla mientras se construye; al ser parcial y sobre pocas filas son instantes.
CREATE INDEX ix_comunicacion_baja_enviada ON comunicacion_baja (enviada_en) WHERE estado = 'ENVIADA';
