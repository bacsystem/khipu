-- #342: constancia de que cada usuario aceptó los términos del servicio y la política de privacidad, en qué versión y cuándo.
-- Una fila por usuario y versión: subir la versión no borra la aceptación anterior, se suma la nueva. Los usuarios que ya existían
-- no tienen ninguna fila, así que quedan pendientes y el portal les pide aceptar la versión vigente la próxima vez que entren.
CREATE TABLE aceptacion_terminos (
    usuario_id  UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    version     VARCHAR(40) NOT NULL,
    aceptada_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (usuario_id, version)
);
