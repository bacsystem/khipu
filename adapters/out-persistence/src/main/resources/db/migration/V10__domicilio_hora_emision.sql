-- Domicilio fiscal del emisor (RegistrationAddress: ubigeo catálogo 13, dirección, urbanización, distrito/provincia/departamento,
-- establecimiento anexo) y cuenta de detracciones por defecto; hora de emisión del comprobante (cbc:IssueTime).
ALTER TABLE tenant
    ADD COLUMN dom_ubigeo          VARCHAR(6),
    ADD COLUMN dom_direccion       VARCHAR(200),
    ADD COLUMN dom_urbanizacion    VARCHAR(25),
    ADD COLUMN dom_distrito        VARCHAR(30),
    ADD COLUMN dom_provincia       VARCHAR(30),
    ADD COLUMN dom_departamento    VARCHAR(30),
    ADD COLUMN dom_establecimiento VARCHAR(4),
    ADD COLUMN cuenta_detracciones VARCHAR(20);

ALTER TABLE documento
    ADD COLUMN hora_emision TIME;
