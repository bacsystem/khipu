-- Segundo factor del administrador (#177). Tablas aparte de `administrador` para que el estado del 2FA (que cambia en cada login)
-- no reescriba la fila de la identidad. Sin fila = nunca configuró el segundo factor: no puede operar el backoffice.
CREATE TABLE administrador_segundo_factor (
    administrador_id UUID PRIMARY KEY REFERENCES administrador (id) ON DELETE CASCADE,
    -- Secreto TOTP cifrado con MASTER_KEY (AES-GCM, como las credenciales SOL): con la base sola no se generan códigos.
    secreto_cifrado  BYTEA       NOT NULL,
    -- false mientras el administrador no haya tecleado el primer código: un QR mostrado y no escaneado no cuenta.
    confirmado       BOOLEAN     NOT NULL DEFAULT false,
    -- Último paso TOTP (ventana de 30 s) aceptado: un código de ese paso o anterior ya no vale (anti-reuso).
    ultimo_paso      BIGINT      NOT NULL DEFAULT 0,
    fallos           INT         NOT NULL DEFAULT 0,
    bloqueado_hasta  TIMESTAMPTZ,
    actualizado_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Códigos de recuperación: solo el SHA-256 (son 50 bits al azar, no adivinables por diccionario). Cada uno sirve una vez.
CREATE TABLE administrador_codigo_recuperacion (
    administrador_id UUID        NOT NULL REFERENCES administrador (id) ON DELETE CASCADE,
    hash             CHAR(64)    NOT NULL,
    usado_at         TIMESTAMPTZ,
    PRIMARY KEY (administrador_id, hash)
);
