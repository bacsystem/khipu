-- La bajada de plan no corta a nadie a mitad de ciclo (#191): queda programada para el inicio del ciclo siguiente (medianoche del día 1 en America/Lima) y la cuenta
-- sigue con el plan de hoy hasta entonces. Subir de plan tiene efecto inmediato y no pasa por aquí.
-- Como mucho uno por cuenta: un segundo cambio programado reemplaza al primero, y un cambio inmediato (una subida, una renovación) lo cancela. Cuando la fecha llega,
-- un trabajo programado lo convierte en la suscripción activa (la actual termina en `aplica_desde`) y borra esta fila.
-- Lleva el vencimiento y la gracia con que empezará la suscripción nueva, para que nadie tenga que acordarse de ellos cuando llegue la fecha.
CREATE TABLE suscripcion_cambio_programado (
    cuenta_id      UUID PRIMARY KEY REFERENCES cuenta (id),
    plan_id        UUID        NOT NULL REFERENCES plan (id),
    aplica_desde   TIMESTAMPTZ NOT NULL,
    vence_en       TIMESTAMPTZ,
    dias_de_gracia INTEGER     NOT NULL DEFAULT 0 CHECK (dias_de_gracia >= 0),
    CONSTRAINT ck_cambio_programado_vence CHECK (vence_en IS NULL OR vence_en > aplica_desde)
);
-- El trabajo que aplica lo vencido busca por fecha.
CREATE INDEX ix_cambio_programado_aplica ON suscripcion_cambio_programado (aplica_desde);
-- Cuántas cuentas esperan pasar a cada plan (un plan al que alguien va a pasar no se puede borrar).
CREATE INDEX ix_cambio_programado_plan ON suscripcion_cambio_programado (plan_id);
