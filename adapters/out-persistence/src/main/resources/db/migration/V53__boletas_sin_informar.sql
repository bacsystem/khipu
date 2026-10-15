-- Boleta que nunca debe quedar sin informarse a SUNAT (#356). La cola de errores del backoffice y las métricas del portal ahora cuentan también las boletas FIRMADAS que el
-- cliente dejó sin enviar (enviar_automatico = false) desde el 5.º día. FIRMADO no está en ix_documento_en_error (V42) y es un estado de paso: casi todo lo firmado sale
-- enviado en la misma llamada, así que las que se quedan son pocas. Un índice parcial las encuentra sin recorrer la tabla.
CREATE INDEX ix_documento_firmado ON documento (fecha_emision, created_at, id) WHERE estado = 'FIRMADO';
