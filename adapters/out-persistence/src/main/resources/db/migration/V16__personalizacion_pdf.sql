-- Diseño de la representación impresa por empresa (no afecta al XML) y observaciones libres por comprobante (solo PDF).
ALTER TABLE tenant
    ADD COLUMN pdf_plantilla      VARCHAR(12)  NOT NULL DEFAULT 'CLASICO' CHECK (pdf_plantilla IN ('CLASICO', 'MODERNO', 'SUTIL', 'CORPORATIVO', 'GRIS')),
    ADD COLUMN pdf_color          VARCHAR(7)   NOT NULL DEFAULT '#1E1E24',
    ADD COLUMN pdf_logo_key       VARCHAR(200),
    ADD COLUMN pdf_pie            VARCHAR(300),
    ADD COLUMN pdf_observaciones  VARCHAR(1000);

ALTER TABLE comprobante ADD COLUMN observaciones VARCHAR(1000);
