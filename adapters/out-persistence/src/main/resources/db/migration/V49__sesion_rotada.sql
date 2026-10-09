-- S6: un refresh recién rotado sigue sirviendo unos segundos, para la petición de otra instancia del portal que mandó el mismo refresh.
-- Solo la rotación da esa gracia: revocar por logout o al restablecer la contraseña deja las dos columnas en NULL.
-- Columnas nulas sin valor por defecto: agregarlas no reescribe la tabla.
ALTER TABLE sesion ADD COLUMN rotada_en TIMESTAMPTZ;
ALTER TABLE sesion ADD COLUMN reemplazada_por UUID;
-- La familia: todo lo que salió de un mismo login (rotaciones y ramas abiertas en gracia). El logout la cierra entera. Nula en las sesiones de antes:
-- su familia es su propio id. Con índice, porque el logout la busca.
ALTER TABLE sesion ADD COLUMN familia_id UUID;
CREATE INDEX sesion_familia_idx ON sesion (familia_id);
