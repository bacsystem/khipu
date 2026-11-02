-- El plan como dato (#189): lo que se vende (precio y límites) y la suscripción que liga a una cuenta con uno. Hasta ahora el plan solo existía como texto en la
-- página de precios; esta es la base para asignarlo, cobrarlo y hacerlo valer.

CREATE TABLE plan (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre            VARCHAR(40)   NOT NULL,
    precio_mensual    NUMERIC(10,2) NOT NULL CHECK (precio_mensual >= 0),
    -- Los límites en NULL son «sin límite»; nunca cero (un plan que no deja emitir un documento no se vende).
    documentos_al_mes INTEGER       CHECK (documentos_al_mes > 0),
    rucs              INTEGER       NOT NULL CHECK (rucs > 0),
    usuarios          INTEGER       CHECK (usuarios > 0),
    api_keys          INTEGER       CHECK (api_keys > 0),
    retencion_anios   INTEGER       NOT NULL CHECK (retencion_anios > 0),
    estado            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    -- El plan con el que nace toda cuenta nueva. Uno solo, y siempre activo: sin él una cuenta nueva quedaría sin plan.
    por_defecto       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_plan_defecto_activo CHECK (NOT por_defecto OR estado = 'ACTIVO')
);
-- El nombre es único sin importar mayúsculas ni espacios de más (el dominio lo recorta).
CREATE UNIQUE INDEX ux_plan_nombre ON plan (lower(nombre));
CREATE UNIQUE INDEX ux_plan_por_defecto ON plan (por_defecto) WHERE por_defecto;

-- Los cuatro planes de la página de precios. La retención de Negocio y Pro no la dice la página: heredan la de Emprende («Todo lo de Emprende»).
INSERT INTO plan (nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, por_defecto) VALUES
    ('Gratis',   0,   30,   1,  1,    1,    1, TRUE),
    ('Emprende', 29,  300,  1,  1,    2,    5, FALSE),
    ('Negocio',  69,  1500, 3,  3,    5,    5, FALSE),
    ('Pro',      129, NULL, 10, NULL, NULL, 5, FALSE);

-- Una cuenta tiene muchas suscripciones a lo largo del tiempo, pero una sola vigente: la que no tiene `termina_en`. Las demás son el historial.
CREATE TABLE suscripcion (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id      UUID        NOT NULL REFERENCES cuenta (id),
    plan_id        UUID        NOT NULL REFERENCES plan (id),
    inicia_en      TIMESTAMPTZ NOT NULL,
    -- Hasta cuándo está pagada (exclusivo). NULL: no vence (el plan gratis).
    vence_en       TIMESTAMPTZ,
    -- Cuánto se sigue sirviendo después de vencida.
    dias_de_gracia INTEGER     NOT NULL DEFAULT 0 CHECK (dias_de_gracia >= 0),
    -- NULL: es la suscripción vigente de la cuenta. Con fecha: la reemplazó otra.
    termina_en     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_suscripcion_vence CHECK (vence_en IS NULL OR vence_en > inicia_en),
    CONSTRAINT ck_suscripcion_termina CHECK (termina_en IS NULL OR termina_en >= inicia_en)
);
-- «No dos suscripciones activas» lo garantiza la base, no solo el código: dos cambios de plan a la vez no pueden dejar a la cuenta con dos.
CREATE UNIQUE INDEX ux_suscripcion_activa ON suscripcion (cuenta_id) WHERE termina_en IS NULL;
-- Cuántas cuentas usan cada plan (el listado de planes, #190).
CREATE INDEX ix_suscripcion_plan_activa ON suscripcion (plan_id) WHERE termina_en IS NULL;
CREATE INDEX ix_suscripcion_cuenta ON suscripcion (cuenta_id, inicia_en);

-- Las cuentas que ya existen migran al plan por defecto, desde el día que se crearon.
INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en)
SELECT c.id, (SELECT id FROM plan WHERE por_defecto), c.created_at FROM cuenta c;

-- Toda cuenta nueva nace con el plan por defecto, sea cual sea el camino por el que se cree (registro, alta asistida, un script): «siempre tiene un plan» no
-- depende de que cada servicio se acuerde. Si no hubiera plan por defecto, crear la cuenta falla en vez de dejarla sin plan.
CREATE FUNCTION cuenta_nace_con_plan() RETURNS TRIGGER AS $$
DECLARE
    base UUID;
BEGIN
    SELECT id INTO base FROM plan WHERE por_defecto;
    IF base IS NULL THEN
        RAISE EXCEPTION 'No hay plan por defecto: una cuenta no puede nacer sin plan';
    END IF;
    INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en) VALUES (NEW.id, base, NEW.created_at);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_cuenta_nace_con_plan AFTER INSERT ON cuenta FOR EACH ROW EXECUTE FUNCTION cuenta_nace_con_plan();
