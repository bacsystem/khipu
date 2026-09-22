-- Datos sectoriales de la detracción por ítem (#69): recursos hidrobiológicos (1002) y transporte de carga (1004), como JSON.
ALTER TABLE comprobante_item ADD COLUMN hidrobiologico JSONB;
ALTER TABLE comprobante_item ADD COLUMN transporte JSONB;
