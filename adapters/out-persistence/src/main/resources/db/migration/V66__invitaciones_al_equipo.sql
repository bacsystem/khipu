-- #21: invitaciones a sumarse a una cuenta con un rol. Del token del enlace solo se guarda el hash; un reenvío lo reemplaza.
CREATE TABLE invitacion (
    id           UUID         PRIMARY KEY,
    cuenta_id    UUID         NOT NULL REFERENCES cuenta (id),
    email        VARCHAR(254) NOT NULL,
    rol          VARCHAR(10)  NOT NULL CHECK (rol IN ('ADMIN', 'EMISOR', 'LECTURA')),
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    expira_en    TIMESTAMPTZ  NOT NULL,
    estado       VARCHAR(10)  NOT NULL CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'REVOCADA')),
    -- Quién invitó; si después lo borran, la invitación queda sin autor en vez de desaparecer.
    invitado_por UUID         REFERENCES usuario (id) ON DELETE SET NULL,
    envios       INT          NOT NULL CHECK (envios >= 1),
    created_at   TIMESTAMPTZ  NOT NULL,
    aceptada_en  TIMESTAMPTZ
);
-- Una sola pendiente por correo en cada cuenta: la última barrera si dos administradores invitan al mismo correo a la vez.
CREATE UNIQUE INDEX invitacion_pendiente_por_correo ON invitacion (cuenta_id, email) WHERE estado = 'PENDIENTE';
-- La lista de pendientes de la página de equipo.
CREATE INDEX invitacion_cuenta_estado ON invitacion (cuenta_id, estado, created_at DESC);
-- Las pendientes de quien invitó, para revocarlas si deja de ser administrador.
CREATE INDEX invitacion_invitado_por ON invitacion (invitado_por) WHERE estado = 'PENDIENTE';

-- Cada correo de invitación que salió (al invitar y al reenviar), aunque la invitación después se revoque: el tope de envíos por cuenta y por destinatario en
-- 24 horas se cuenta aquí, así que revocar e invitar de nuevo no lo reinicia.
CREATE TABLE envio_de_invitacion (
    id         BIGSERIAL    PRIMARY KEY,
    cuenta_id  UUID         NOT NULL REFERENCES cuenta (id),
    email      VARCHAR(254) NOT NULL,
    enviado_en TIMESTAMPTZ  NOT NULL
);
CREATE INDEX envio_de_invitacion_cuenta ON envio_de_invitacion (cuenta_id, enviado_en);
CREATE INDEX envio_de_invitacion_email ON envio_de_invitacion (email, enviado_en);
