-- Listado de cuentas del backoffice (#180).
-- `ultimo_acceso` filtra `usuario` por `cuenta_id` una vez por cada fila de la página, y nada indexaba esa columna.
CREATE INDEX ix_usuario_cuenta ON usuario (cuenta_id);
-- El listado ordena por `created_at DESC, id`: con este índice cada página lee solo sus filas en vez de ordenar todas las cuentas.
CREATE INDEX ix_cuenta_alta ON cuenta (created_at DESC, id);
