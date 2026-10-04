-- Cambiar los límites de un plan afecta al ciclo siguiente, no al que está en curso (#190): subir un tope a mitad de mes no regala documentos del mes
-- corriente, y bajarlo no le corta a nadie. Los límites de `plan` son los que mandan ahora; el cambio decidido espera aquí hasta `aplica_desde`
-- (el inicio del ciclo siguiente, medianoche del día 1 en America/Lima).
-- Como mucho uno por plan: un segundo cambio antes de que llegue la fecha reemplaza al primero. Cuando la fecha llega, el dominio lo da por vigente
-- (Plan.vigenteEn) y la siguiente edición del plan lo pasa a las columnas de `plan` y borra esta fila.
CREATE TABLE plan_cambio_programado (
    plan_id           UUID PRIMARY KEY REFERENCES plan (id) ON DELETE CASCADE,
    aplica_desde      TIMESTAMPTZ NOT NULL,
    documentos_al_mes INTEGER CHECK (documentos_al_mes > 0),
    rucs              INTEGER NOT NULL CHECK (rucs > 0),
    usuarios          INTEGER CHECK (usuarios > 0),
    api_keys          INTEGER CHECK (api_keys > 0),
    retencion_anios   INTEGER NOT NULL CHECK (retencion_anios > 0)
);
