-- Pagos que un administrador registra a mano (#194): quién pagó, qué periodo cubre, cuánto, por qué medio y cuándo. No hay pasarela de pago, a propósito.
-- Solo se agregan: un pago anotado no se edita ni se borra, es el rastro de plata que entró.
CREATE TABLE pago (
    id             UUID PRIMARY KEY,
    cuenta_id      UUID          NOT NULL REFERENCES cuenta (id),
    -- La suscripción vigente cuando se registró el pago.
    suscripcion_id UUID          NOT NULL REFERENCES suscripcion (id),
    periodo_desde  DATE          NOT NULL,
    periodo_hasta  DATE          NOT NULL,
    -- En soles, hasta 9 999 999,99.
    monto          NUMERIC(9, 2) NOT NULL,
    medio          VARCHAR(20)   NOT NULL,
    fecha_de_pago  DATE          NOT NULL,
    referencia     VARCHAR(100),
    nota           VARCHAR(200),
    registrado_en  TIMESTAMPTZ   NOT NULL,
    -- El nuevo vencimiento de la suscripción si este pago lo movió; NULL si solo quedó anotado.
    extendio_hasta TIMESTAMPTZ,
    CONSTRAINT ck_pago_periodo CHECK (periodo_hasta >= periodo_desde AND periodo_hasta <= periodo_desde + INTERVAL '1 year' - INTERVAL '1 day'),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_medio CHECK (medio IN ('TRANSFERENCIA', 'DEPOSITO', 'YAPE', 'PLIN', 'TARJETA', 'EFECTIVO', 'OTRO'))
);
-- El historial de una cuenta, del más reciente al más antiguo.
CREATE INDEX ix_pago_cuenta ON pago (cuenta_id, fecha_de_pago DESC, registrado_en DESC, id);
-- El mismo apunte repetido (doble clic, reintento): una cuenta no repite medio y referencia, sin distinguir mayúsculas. Sin referencia no hay con qué comparar.
CREATE UNIQUE INDEX ux_pago_referencia ON pago (cuenta_id, medio, lower(referencia)) WHERE referencia IS NOT NULL;
