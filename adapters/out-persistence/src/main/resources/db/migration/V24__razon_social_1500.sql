-- La razón social del emisor admite hasta 1500 caracteres (SUNAT 4338, `cbc:RegistrationName`), pero la columna era
-- VARCHAR(250): una razón social larga pasaba el DTO y reventaba en la base con un 500 que invitaba a reintentar.
ALTER TABLE tenant ALTER COLUMN razon_social TYPE VARCHAR(1500);
