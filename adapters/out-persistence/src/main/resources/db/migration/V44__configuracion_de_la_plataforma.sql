-- Lo que un administrador cambia de la plataforma sin un despliegue (#199): el remitente de los correos, el texto de cada correo y el aviso de mantenimiento.
-- Lo que no está acá no se cambió: el remitente sale de la configuración del servidor y cada correo de su texto de fábrica. Los límites repiten los del dominio
-- (RemitenteDeCorreo, PlantillaDeCorreo, BannerDeMantenimiento): un valor que el dominio rechaza no puede entrar por otro camino.
-- `actualizado_por` es el administrador que lo cambió; nulo si fue la clave de la plataforma. Sin FK, como la bitácora: el registro no depende de que el administrador siga existiendo.

-- Una sola fila: el remitente es uno.
CREATE TABLE remitente_correo (
    id             SMALLINT     PRIMARY KEY CHECK (id = 1),
    nombre         VARCHAR(100),
    email          VARCHAR(254) NOT NULL,
    responder_a    VARCHAR(254),
    actualizado_en TIMESTAMPTZ  NOT NULL,
    actualizado_por UUID
);

-- Una fila por correo editado, con el nombre del correo del dominio como clave.
CREATE TABLE plantilla_correo (
    tipo           VARCHAR(40)   PRIMARY KEY,
    asunto         VARCHAR(150)  NOT NULL CHECK (asunto <> ''),
    cuerpo         VARCHAR(5000) NOT NULL CHECK (cuerpo <> ''),
    actualizado_en TIMESTAMPTZ   NOT NULL,
    actualizado_por UUID
);

-- Una sola fila: hay un aviso a la vez. Siempre tiene fin, y el fin es posterior al inicio.
CREATE TABLE banner_mantenimiento (
    id             SMALLINT     PRIMARY KEY CHECK (id = 1),
    texto          VARCHAR(300) NOT NULL CHECK (texto <> ''),
    desde          TIMESTAMPTZ  NOT NULL,
    hasta          TIMESTAMPTZ  NOT NULL,
    actualizado_en TIMESTAMPTZ  NOT NULL,
    actualizado_por UUID,
    CHECK (hasta > desde)
);
