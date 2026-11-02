-- La cola global de errores del backoffice (#196) lee los comprobantes con problema de todas las empresas: error de envío, fuera de plazo y rechazados (de estos, solo los
-- de formato). Son una fracción mínima de la tabla; un índice parcial los encuentra, ya en el orden de la cola (la emisión más antigua primero), sin recorrerla.
CREATE INDEX ix_documento_en_error ON documento (fecha_emision, created_at, id)
    WHERE estado IN ('ERROR_ENVIO', 'FUERA_DE_PLAZO', 'RECHAZADO');
