-- #301: un resumen diario (RC) lleva hasta 500 líneas de una misma fecha de emisión (SFS 0158). Cada línea sigue siendo una fila de comunicacion_baja (la de su comprobante), y las
-- filas de un mismo RC comparten fecha de generación y correlativo —es decir, identificador, XML, ticket y CDR—. `linea` es el LineID de la fila en el resumen y `lineas` cuántas
-- tiene el resumen (1 en todo lo anterior y en toda baja). El RA (comunicación de baja) sigue siendo de un solo comprobante.
ALTER TABLE comunicacion_baja ADD COLUMN linea  INTEGER NOT NULL DEFAULT 1;
ALTER TABLE comunicacion_baja ADD COLUMN lineas INTEGER NOT NULL DEFAULT 1;
ALTER TABLE comunicacion_baja ADD CONSTRAINT comunicacion_baja_linea_valida CHECK (linea BETWEEN 1 AND 500 AND lineas BETWEEN linea AND 500);

-- El identificador ya no es único por fila sino por (fecha, correlativo, línea): el índice de la restricción anterior sirve también para leer un resumen entero.
ALTER TABLE comunicacion_baja DROP CONSTRAINT comunicacion_baja_tenant_id_fecha_generacion_correlativo_key;
ALTER TABLE comunicacion_baja ADD CONSTRAINT comunicacion_baja_resumen_linea_key UNIQUE (tenant_id, fecha_generacion, correlativo, linea);

-- Un correlativo del día es de un solo resumen o de una sola comunicación de baja, pero la base ya no lo garantiza (un RA en la línea 1 y un RC en las líneas 2 y siguientes cabrían):
-- lo garantiza `JdbcBajaRepository.siguienteCorrelativo`, que toma el máximo del día bajo el lock de la fila del tenant. No hay otro camino que asigne correlativos.
