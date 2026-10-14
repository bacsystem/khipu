-- El monitor global (#195, #362 B14) pregunta cada 30 segundos cuántos resúmenes diarios y comunicaciones de baja siguen ENVIADOS (con ticket, sin respuesta de SUNAT) y
-- cuándo se creó el más antiguo. `comunicacion_baja` crece con cada boleta informada o anulada y los ENVIADOS son casi siempre cero o pocos: un índice parcial hace la
-- lectura instantánea sin cargar las escrituras (solo entran las filas mientras están ENVIADAS). Es un índice normal (no CONCURRENTLY: Flyway corre cada migración en una
-- transacción), que bloquea las escrituras de la tabla mientras se construye; al ser parcial y sobre pocas filas son instantes.
CREATE INDEX ix_comunicacion_baja_enviada ON comunicacion_baja (created_at) WHERE estado = 'ENVIADA';
