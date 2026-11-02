-- #261: contadores de intentos con ventana de tiempo (fallos de login por correo y por IP, correos de recuperación por dirección).
-- La clave es un texto opaco que arma la aplicación («login:cliente:<correo>», «login:ip:<ip>», «recuperar:<correo>»): la tabla no
-- sabe qué cuenta. Sin FK a usuario a propósito: se cuenta también un correo que no existe, para no revelar qué cuentas hay.
CREATE TABLE intento_de_acceso (
    clave            TEXT PRIMARY KEY,
    intentos         INT NOT NULL CHECK (intentos >= 0),
    ventana_desde    TIMESTAMPTZ NOT NULL,
    bloqueado_hasta  TIMESTAMPTZ
);

-- Para la purga periódica de contadores viejos.
CREATE INDEX intento_de_acceso_ventana_idx ON intento_de_acceso (ventana_desde);
