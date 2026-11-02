-- El monitor global de emisión (#195) cuenta los comprobantes de todas las empresas creados en las últimas 24 horas. Los índices que había arrancan por `tenant_id`, así
-- que esa lectura recorría toda la tabla; este permite ir directo a lo reciente. Es un índice normal (no CONCURRENTLY: Flyway corre cada migración en una transacción), que
-- bloquea las escrituras de `documento` mientras se construye; con el volumen de hoy son instantes.
CREATE INDEX ix_documento_created_at ON documento (created_at);
