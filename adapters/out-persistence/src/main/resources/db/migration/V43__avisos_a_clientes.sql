-- Los avisos que un administrador le manda a un cliente desde el backoffice (#197): que su certificado está por vencer o vencido, o que SUNAT no acepta sus credenciales SOL.
-- El registro es lo que impide repetir el mismo aviso todos los días: se consulta por empresa y motivo, del más reciente al más antiguo. La fila se reserva antes de mandar
-- el correo y se borra si el correo falla, así que lo que queda es lo que salió.
CREATE TABLE aviso_a_cliente (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenant (id),
    -- La cuenta a la que se le escribió; las empresas de integración no tienen y no se les avisa.
    cuenta_id    UUID         NOT NULL REFERENCES cuenta (id),
    motivo       VARCHAR(30)  NOT NULL CHECK (motivo IN ('CERTIFICADO_POR_VENCER', 'CERTIFICADO_VENCIDO', 'CREDENCIALES_SOL_INVALIDAS')),
    destinatario VARCHAR(254) NOT NULL,
    enviado_en   TIMESTAMPTZ  NOT NULL,
    -- El administrador que lo mandó; nulo si lo mandó la clave de la plataforma. Sin FK, como la bitácora: el registro no depende de que el administrador siga existiendo.
    enviado_por  UUID
);
CREATE INDEX ix_aviso_a_cliente_empresa_motivo ON aviso_a_cliente (tenant_id, motivo, enviado_en DESC);
