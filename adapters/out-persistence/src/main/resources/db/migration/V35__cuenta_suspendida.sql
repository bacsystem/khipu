-- Suspender una cuenta (#182): corta el portal y la emisión por API de todas sus empresas sin borrar nada. Reactivarla es volver a poner NULL.
-- Nulo = activa. La fecha dice desde cuándo, y es lo que muestra el backoffice.
ALTER TABLE cuenta ADD COLUMN suspendida_en TIMESTAMPTZ;
