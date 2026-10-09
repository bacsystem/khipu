-- S2: de qué pepper es el hash de cada API key, para poder rotar API_KEY_PEPPER sin invalidar las ya emitidas. Es una huella (los primeros 16
-- hex de un SHA-256 del pepper), no el pepper. Las keys de antes quedan sin huella y el backend la completa al arrancar: con la del pepper anterior
-- si se está rotando, con la del vigente si no.
ALTER TABLE api_key ADD COLUMN pepper_huella VARCHAR(16);
