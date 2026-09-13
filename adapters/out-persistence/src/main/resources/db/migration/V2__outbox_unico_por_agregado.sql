-- Una sola fila pendiente por (agregado, accion): programar() usa ON CONFLICT DO NOTHING y es idempotente,
-- de modo que el servicio de envío y el OutboxWorker pueden invocarlo sin duplicar reintentos.
CREATE UNIQUE INDEX ux_outbox_agregado_accion ON outbox (agregado_id, accion);

-- El índice parcial "WHERE locked_until IS NULL" no cubría filas con lock vencido (locked_until < now());
-- se reemplaza por un índice compuesto que sirve a la consulta de tomarVencidas en ambos casos.
DROP INDEX ix_outbox_pendientes;
CREATE INDEX ix_outbox_pendientes ON outbox (siguiente_intento, locked_until);
