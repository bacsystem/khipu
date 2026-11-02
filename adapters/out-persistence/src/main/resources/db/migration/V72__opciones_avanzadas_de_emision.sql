-- Preferencia de la empresa: si el modal de emisión del portal ofrece las opciones avanzadas (tipo de operación/exportación, anticipos, percepción,
-- retención, más opciones y referencias). Apagada por defecto: la mayoría emite facturas simples. Crédito, detracción y correo se ofrecen siempre.
-- Solo cambia lo que muestra el portal; la API acepta los mismos campos con o sin ella.
ALTER TABLE tenant ADD COLUMN opciones_avanzadas_emision BOOLEAN NOT NULL DEFAULT false;
