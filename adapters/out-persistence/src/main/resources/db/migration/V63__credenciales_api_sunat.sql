-- #318: credenciales de la «API SUNAT» de cada empresa (client_id y client_secret de «Credenciales de API SUNAT» en SOL), cifradas con la MASTER_KEY como las SOL y el
-- certificado. Las usa la consulta integrada de validez por REST, y las usarán la GRE y el SIRE. Van juntas o no van: la base no admite media credencial.
-- Están en JdbcSecretosCifradosRepository.COLUMNAS para que la rotación de la MASTER_KEY también las recifre.
ALTER TABLE tenant ADD COLUMN api_sunat_client_id_enc     BYTEA;
ALTER TABLE tenant ADD COLUMN api_sunat_client_secret_enc BYTEA;
ALTER TABLE tenant ADD CONSTRAINT tenant_api_sunat_completa CHECK ((api_sunat_client_id_enc IS NULL) = (api_sunat_client_secret_enc IS NULL));
