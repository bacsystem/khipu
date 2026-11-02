-- El detalle de una cuenta en el backoffice (#181) lista las últimas acciones del administrador sobre ella: sin este índice, cada
-- apertura de una cuenta recorre toda la bitácora. Orden descendente porque siempre se lee lo más reciente.
CREATE INDEX ix_auditoria_cuenta ON auditoria_admin (cuenta_id, ocurrido_en DESC);
