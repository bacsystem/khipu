-- Listado de cuentas del backoffice (#180).
-- `ultimo_acceso` filtra `usuario` por `cuenta_id` una vez por cada fila de la página, y nada indexaba esa columna.
CREATE INDEX ix_usuario_cuenta ON usuario (cuenta_id);
-- El listado ordena por `created_at DESC, id`: con este índice cada página lee solo sus filas en vez de ordenar todas las cuentas.
CREATE INDEX ix_cuenta_alta ON cuenta (created_at DESC, id);
-- `ultimo_acceso` es `max(created_at)` de las sesiones del usuario: con la fecha en el índice se lee del final en vez de recorrer
-- todas las sesiones acumuladas. Sirve también a las búsquedas solo por `usuario_id`, así que reemplaza a `ix_sesion_usuario` (V3):
-- conservar los dos haría que cada inicio de sesión y cada refresco de token mantuviera dos índices con la misma columna inicial.
CREATE INDEX ix_sesion_acceso ON sesion (usuario_id, created_at DESC);
DROP INDEX ix_sesion_usuario;
