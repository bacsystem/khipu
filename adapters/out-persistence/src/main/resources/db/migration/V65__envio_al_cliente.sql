-- #325: envío automático del comprobante aceptado a su cliente.

-- El correo del cliente que viaja con el comprobante (no va al XML ni a SUNAT). Opcional: sin él no se envía nada.
ALTER TABLE comprobante ADD COLUMN receptor_email VARCHAR(150);

-- La preferencia de la empresa: apagada por defecto, para que nadie empiece a recibir correos que la empresa no pidió.
ALTER TABLE tenant ADD COLUMN envio_automatico_al_cliente BOOLEAN NOT NULL DEFAULT FALSE;

-- Un envío por comprobante (la clave primaria lo garantiza): se anota en la misma transacción en que el comprobante pasa a aceptado (outbox transaccional) y un segundo paso a
-- aceptado, o una segunda réplica, no anota otro. Es también el registro que el emisor ve en el detalle del comprobante.
CREATE TABLE envio_al_cliente (
    documento_id       UUID         PRIMARY KEY REFERENCES documento (id) ON DELETE CASCADE,
    tenant_id          UUID         NOT NULL,
    destinatario       VARCHAR(150) NOT NULL,
    -- PENDIENTE, ENVIADO, FALLIDO (agotó los intentos o no se puede enviar) u OMITIDO (no correspondía al llegar su turno: sin SMTP, comprobante anulado).
    estado             VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE',
    intentos           INT          NOT NULL DEFAULT 0,
    siguiente_intento  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until       TIMESTAMPTZ,
    ultimo_error       VARCHAR(300),
    ultimo_intento_en  TIMESTAMPTZ,
    enviado_en         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_envio_al_cliente_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'OMITIDO'))
);
-- Lo que mira la pasada de envío: solo lo pendiente, por hora de siguiente intento.
CREATE INDEX ix_envio_al_cliente_pendiente ON envio_al_cliente (siguiente_intento) WHERE estado = 'PENDIENTE';
