-- Respuesta de la operación, cifrada con MASTER_KEY (#219): el alta asistida devuelve una API key que solo se guarda como hash, así
-- que un reintento no la puede reconstruir. Vive una hora; después se borra y queda solo la clave (ver LimpiarIdempotenciaService).
ALTER TABLE idempotencia ADD COLUMN respuesta_cifrada BYTEA;
