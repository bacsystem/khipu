-- Dos límites nuevos del plan (#613).
--
-- periodo_documentos: el tope de documentos de Producción se cuenta en el mes calendario (MENSUAL, se renueva el día 1) o en toda la vida de la cuenta (TOTAL,
-- no se renueva). Un tope TOTAL siempre tiene cifra: ilimitado solo puede ser mensual.
--
-- pruebas_beta_por_ruc: las pruebas en Beta al mes que da el plan por cada RUC que permite; la bolsa de la cuenta es esa cifra por `rucs`, compartida entre sus
-- empresas. NULL es «sin límite», nunca cero, como los demás límites. Hasta ahora Beta no tenía tope (#580).
--
-- Van también en plan_cambio_programado, porque son límites: cambiarlos espera al ciclo siguiente como cualquier otro (#190).
ALTER TABLE plan
    ADD COLUMN periodo_documentos   VARCHAR(7) NOT NULL DEFAULT 'MENSUAL' CHECK (periodo_documentos IN ('MENSUAL', 'TOTAL')),
    ADD COLUMN pruebas_beta_por_ruc INTEGER    DEFAULT 1000 CHECK (pruebas_beta_por_ruc > 0),
    ADD CONSTRAINT ck_plan_total_con_cifra CHECK (periodo_documentos = 'MENSUAL' OR documentos_al_mes IS NOT NULL);

ALTER TABLE plan_cambio_programado
    ADD COLUMN periodo_documentos   VARCHAR(7) NOT NULL DEFAULT 'MENSUAL' CHECK (periodo_documentos IN ('MENSUAL', 'TOTAL')),
    ADD COLUMN pruebas_beta_por_ruc INTEGER    DEFAULT 1000 CHECK (pruebas_beta_por_ruc > 0),
    ADD CONSTRAINT ck_plan_cambio_total_con_cifra CHECK (periodo_documentos = 'MENSUAL' OR documentos_al_mes IS NOT NULL);

-- Gratis (el plan gratuito con el que nace toda cuenta) pasa a tope total, como decidió el dueño en #613. Al instante y no al ciclo siguiente: es la regla del plan
-- desde este release. Si el plan por defecto no es gratuito o no tiene cifra, no se toca.
-- Un cambio de límites que ese plan tenga programado también, o al llegar su fecha lo devolvería a mensual.
UPDATE plan SET periodo_documentos = 'TOTAL' WHERE por_defecto AND precio_mensual = 0 AND documentos_al_mes IS NOT NULL;
UPDATE plan_cambio_programado c SET periodo_documentos = 'TOTAL'
FROM plan p WHERE p.id = c.plan_id AND p.periodo_documentos = 'TOTAL' AND c.documentos_al_mes IS NOT NULL;
