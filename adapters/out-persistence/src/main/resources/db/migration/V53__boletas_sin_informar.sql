-- Boleta que nunca debe quedar sin informarse a SUNAT (#356). La cola de errores del backoffice, el monitor y las métricas del portal ahora buscan las boletas que siguen por
-- enviar —FIRMADAS que el cliente dejó sin enviar (enviar_automatico = false), o con el envío fallando— desde el 5.º día. FIRMADO no está en ix_documento_en_error (V42) y, en
-- los dos estados, son un estado de paso: casi todo lo firmado sale enviado en la misma llamada, así que las que se quedan son pocas. Un índice parcial las encuentra sin
-- recorrer la tabla; su predicado es el mismo IN que usan las consultas, y la fecha de emisión primero deja el corte de «desde el 5.º día» como un rango.
CREATE INDEX ix_documento_por_enviar ON documento (fecha_emision, created_at, id) WHERE estado IN ('FIRMADO', 'ERROR_ENVIO');

-- Los avisos de boletas sin informar (#356) los manda el sistema solo, una vez por semana y por empresa, y se registran en la misma tabla que los de un administrador: el registro
-- es lo que impide repetirlos. Dos motivos nuevos: boletas que llegan al 5.º día sin informarse, y la más antigua en el 7.º, el último del resumen diario.
ALTER TABLE aviso_a_cliente DROP CONSTRAINT aviso_a_cliente_motivo_check;
ALTER TABLE aviso_a_cliente ADD CONSTRAINT aviso_a_cliente_motivo_check
    CHECK (motivo IN ('CERTIFICADO_POR_VENCER', 'CERTIFICADO_VENCIDO', 'CREDENCIALES_SOL_INVALIDAS', 'BOLETAS_POR_INFORMAR', 'BOLETAS_POR_VENCER_PLAZO'));
