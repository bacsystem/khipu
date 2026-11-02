-- LOG-03 (auditoría 2026-10-22): la cantidad y el precio unitario de la línea admiten 12 enteros y 10 decimales en el dominio (an..12,10, el formato de
-- SUNAT) y el XML se firma con ellos, pero las columnas quedaron en NUMERIC(14,4) desde V1: Postgres redondeaba en silencio (0.8474576271 → 0.8475), el
-- comprobante rehidratado recalculaba totales distintos de los firmados, y con 11 o 12 enteros el INSERT desbordaba después de firmar y subir el XML.
-- Mismo tipo que producto.precio_unitario (V60) y guia_remision_item.cantidad (V69).
-- Cambiar la escala reescribe la tabla y la bloquea mientras tanto: aplicar en ventana. Hoy es chica; si creciera, hacerlo por copia (columna nueva,
-- backfill por lotes, swap) en vez de este ALTER.
ALTER TABLE comprobante_item
    ALTER COLUMN cantidad TYPE NUMERIC(22, 10),
    ALTER COLUMN precio_unitario TYPE NUMERIC(22, 10);
