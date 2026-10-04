-- Verificación del correo (#22). Nulo = sin verificar: puede entrar al portal pero no crear empresas ni emitir.
ALTER TABLE usuario ADD COLUMN correo_verificado_at TIMESTAMPTZ;
-- Los usuarios que ya existen quedan verificados: entraron cuando no se pedía, y bloquearlos de golpe les cortaría la emisión.
UPDATE usuario SET correo_verificado_at = now();

-- Tokens del enlace de verificación: solo el hash, 24 h de vida, un solo uso. Aparte de token_recuperacion para que un enlace de
-- verificación no sirva para cambiar la contraseña.
CREATE TABLE token_verificacion (
    token_hash VARCHAR(64) PRIMARY KEY,
    usuario_id UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    expira_en  TIMESTAMPTZ NOT NULL,
    usado      BOOLEAN     NOT NULL DEFAULT false
);
