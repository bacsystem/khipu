-- #107: SUNAT rechazó las credenciales SOL de la empresa (usuario o clave incorrectos, usuario no secundario…). Mientras esté puesta, el outbox no toma
-- sus envíos; guardar credenciales nuevas la quita. `sol_rechazadas_en` es el primer rechazo; el motivo, el último que dio SUNAT. `sol_sondeo_en` es el
-- último rechazo o el último envío de prueba: pasada una hora, el outbox prueba con un solo envío, así un rechazo pasajero no pausa para siempre.
ALTER TABLE tenant ADD COLUMN sol_rechazadas_en TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN sol_rechazo_motivo VARCHAR(500);
ALTER TABLE tenant ADD COLUMN sol_sondeo_en TIMESTAMPTZ;
