-- #19: webhooks de cambio de estado de comprobante.
-- Un endpoint es un destino de la empresa (URL https, eventos a los que se suscribe, secreto de firma cifrado con la MASTER_KEY).
CREATE TABLE webhook_endpoint (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenant (id),
    url                 VARCHAR(500) NOT NULL,
    descripcion         VARCHAR(100),
    -- Nombres de evento separados por coma («comprobante.aceptado,comprobante.rechazado»); se compara con LIKE al encolar.
    eventos             VARCHAR(400) NOT NULL,
    secreto_enc         BYTEA        NOT NULL,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    pausado_motivo      VARCHAR(300),
    fallos_consecutivos INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_webhook_endpoint_tenant ON webhook_endpoint (tenant_id, created_at);

-- Una entrega es un evento para un endpoint, con el cuerpo exacto que se firma. Se encola en la misma transacción que el cambio de estado (outbox transaccional).
CREATE TABLE webhook_entrega (
    id                 UUID PRIMARY KEY,
    endpoint_id        UUID        NOT NULL REFERENCES webhook_endpoint (id) ON DELETE CASCADE,
    tenant_id          UUID        NOT NULL,
    evento             VARCHAR(40) NOT NULL,
    documento_id       UUID,
    payload            TEXT        NOT NULL,
    estado             VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    intentos           INT         NOT NULL DEFAULT 0,
    siguiente_intento  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until       TIMESTAMPTZ,
    ultimo_http        INT,
    ultimo_error       VARCHAR(300),
    ultimo_intento_en  TIMESTAMPTZ,
    entregada_en       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Lo que mira el trabajo de entrega: solo lo pendiente, por hora de siguiente intento.
CREATE INDEX ix_webhook_entrega_pendiente ON webhook_entrega (siguiente_intento) WHERE estado = 'PENDIENTE';
-- El log de un endpoint, lo más reciente primero.
CREATE INDEX ix_webhook_entrega_endpoint ON webhook_entrega (endpoint_id, created_at DESC);
-- La limpieza por antigüedad de lo ya cerrado.
CREATE INDEX ix_webhook_entrega_cerradas ON webhook_entrega (created_at) WHERE estado <> 'PENDIENTE';
