-- Limpieza de sesiones (#611): el borrado por lotes busca por vencimiento. Ningún índice tenía `expira_en`, así que cada lote recorría la tabla.
-- Es un índice normal (no CONCURRENTLY: Flyway corre cada migración en una transacción), como V41 y V52.
-- Bloquea las escrituras en `sesion` (login y refresh) mientras se construye: antes del deploy, mirar `SELECT count(*) FROM sesion` en producción.
CREATE INDEX ix_sesion_expira ON sesion (expira_en);
