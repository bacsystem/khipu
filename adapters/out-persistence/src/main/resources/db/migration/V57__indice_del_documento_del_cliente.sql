-- Listado de comprobantes por el documento del cliente (#326). El tenant siempre acota primero (ix_documento_tenant_*). El tipo no lleva índice propio: tiene cuatro valores
-- y un índice (tenant_id, tipo, …) competía con ix_documento_tenant_fecha en los conteos por mes de la empresa sin acelerar nada.
-- Prefijo del documento (LIKE 'abc%'): text_pattern_ops sirve con cualquier collation.
CREATE INDEX ix_comprobante_receptor_doc ON comprobante (receptor_num_doc text_pattern_ops);
