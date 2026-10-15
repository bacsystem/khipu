-- Boleta que nunca debe quedar sin informarse a SUNAT (#356). La cola de errores del backoffice, el monitor y las métricas del portal ahora buscan las boletas que siguen por
-- enviar —FIRMADAS que el cliente dejó sin enviar (enviar_automatico = false), o con el envío fallando— desde el 5.º día. FIRMADO no está en ix_documento_en_error (V42) y, en
-- los dos estados, son un estado de paso: casi todo lo firmado sale enviado en la misma llamada, así que las que se quedan son pocas. Un índice parcial las encuentra sin
-- recorrer la tabla; su predicado es el mismo IN que usan las consultas, y la fecha de emisión primero deja el corte de «desde el 5.º día» como un rango.
CREATE INDEX ix_documento_por_enviar ON documento (fecha_emision, created_at, id) WHERE estado IN ('FIRMADO', 'ERROR_ENVIO');
