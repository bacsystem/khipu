-- Listado de comprobantes por tipo y por documento del cliente (#326). El tenant siempre acota primero; estos índices evitan recorrer todos sus comprobantes.
CREATE INDEX ix_documento_tenant_tipo ON documento (tenant_id, tipo, created_at DESC);
-- Prefijo del documento (LIKE 'abc%'): text_pattern_ops sirve con cualquier collation.
CREATE INDEX ix_comprobante_receptor_doc ON comprobante (receptor_num_doc text_pattern_ops);
