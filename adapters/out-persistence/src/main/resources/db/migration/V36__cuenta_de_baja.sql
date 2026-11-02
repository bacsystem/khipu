-- Baja lógica de un cliente que se fue (#201): lo saca de los listados operativos y del cobro, sin borrar nada. Reponerla es volver a poner NULL.
-- Nulo = en servicio. La fecha dice desde cuándo. Es independiente de la suspensión (#182): una cuenta puede estar suspendida, de baja, las dos o ninguna.
-- No se toca `tenant`: la empresa y su RUC se conservan (la retención de comprobantes, XML y CDR es una obligación legal del emisor).
ALTER TABLE cuenta ADD COLUMN baja_en TIMESTAMPTZ;
