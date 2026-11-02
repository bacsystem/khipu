-- Claves de idempotencia (#115): un reintento de la misma operación devuelve lo que ya se hizo. `alcance` separa operaciones y
-- empresas ('factura:<tenant>'); la clave la elige el cliente. La clave primaria es la que hace esperar a un pedido simultáneo con
-- la misma clave hasta que el primero confirme o se revierta.
CREATE TABLE idempotencia (
    alcance     VARCHAR(100) NOT NULL,
    clave       VARCHAR(100) NOT NULL,
    -- SHA-256 del contenido del pedido: la misma clave con otro contenido se rechaza.
    huella      CHAR(64)     NOT NULL,
    recurso_id  UUID,
    creado_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (alcance, clave)
);

-- La limpieza borra por antigüedad.
CREATE INDEX ix_idempotencia_creado ON idempotencia (creado_at);
