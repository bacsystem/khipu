-- Tasa especial del IGV (Padrón de restaurantes y hoteles, Ley 31556): flag por empresa y tasa aplicada por comprobante (#84).
ALTER TABLE tenant ADD COLUMN padron_tasa_especial_igv BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comprobante ADD COLUMN tasa_igv NUMERIC(5,2) NOT NULL DEFAULT 18.00;
