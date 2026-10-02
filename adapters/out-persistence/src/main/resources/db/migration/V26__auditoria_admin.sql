-- Bitácora de las acciones del administrador de la plataforma (#178): quién, qué, sobre qué cuenta o empresa, cuándo y desde qué IP.
-- Sin FK a administrador/cuenta/tenant a propósito: el registro de lo que pasó no debe depender de que el objeto siga existiendo
-- (la baja lógica de un cliente conserva su historial, #201). El actor lo identifica el tipo + el id; la clave de plataforma
-- (X-Platform-Key) no es una persona y por eso no lleva administrador_id.
CREATE TABLE auditoria_admin (
    id               UUID PRIMARY KEY,
    actor_tipo       VARCHAR(20)  NOT NULL CHECK (actor_tipo IN ('ADMINISTRADOR', 'CLAVE_PLATAFORMA')),
    administrador_id UUID,
    accion           VARCHAR(40)  NOT NULL,
    cuenta_id        UUID,
    tenant_id        UUID,
    detalle          VARCHAR(500),
    ip               VARCHAR(45)  NOT NULL,
    ocurrido_en      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT auditoria_admin_actor_coherente CHECK ((actor_tipo = 'ADMINISTRADOR') = (administrador_id IS NOT NULL))
);
