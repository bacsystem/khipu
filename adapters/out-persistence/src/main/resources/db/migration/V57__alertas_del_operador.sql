-- #341: alertas al operador de la plataforma.
-- Qué alerta está activa y cuándo se avisó por última vez, para avisar una vez por problema y no en cada pasada del vigilante.
CREATE TABLE alerta_operador (
    clave         VARCHAR(60) PRIMARY KEY,
    activa        BOOLEAN     NOT NULL,
    desde         TIMESTAMPTZ NOT NULL,
    ultimo_envio  TIMESTAMPTZ NOT NULL
);

-- Latidos de procesos que corren fuera de la aplicación (el respaldo diario, deploy/respaldo): el proceso anota aquí cuándo terminó bien
-- y el vigilante avisa si pasa demasiado tiempo sin un latido.
CREATE TABLE latido_operativo (
    clave   VARCHAR(60) PRIMARY KEY,
    ultimo  TIMESTAMPTZ NOT NULL
);
