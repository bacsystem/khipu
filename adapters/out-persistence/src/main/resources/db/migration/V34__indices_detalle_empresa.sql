-- Detalle de una empresa del backoffice (#186): sus API keys y los cambios de estado de sus comprobantes se leen por empresa y por comprobante.
-- Sin estos índices cada lectura recorre la tabla entera de todas las empresas.
CREATE INDEX ix_api_key_tenant ON api_key (tenant_id, created_at DESC);
CREATE INDEX ix_evento_documento ON evento_documento (documento_id, ocurrido_en DESC);
