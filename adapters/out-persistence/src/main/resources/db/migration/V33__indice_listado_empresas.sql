-- Listado de empresas del backoffice (#185): de la más reciente a la más antigua, paginado. Sin este índice cada página ordena todas las empresas.
CREATE INDEX ix_tenant_alta ON tenant (created_at DESC, id);
