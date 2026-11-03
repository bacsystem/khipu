-- El entorno en que se emitió cada documento (#580): el tope y el consumo del plan cuentan solo Producción, y el entorno de la empresa no alcanza para saberlo,
-- porque cambia (Beta → Producción, casi siempre a mitad de mes). Las pruebas que hizo en Beta antes del cambio no deben pasar a consumir su plan.
ALTER TABLE documento ADD COLUMN entorno VARCHAR(12);

-- Los documentos que ya existen: el entorno que tenía la empresa cuando se crearon. El cambio de entorno solo lo hace el backoffice, y siempre deja bitácora
-- («desde=BETA hacia=PRODUCCION»), así que se reconstruye: el destino del último cambio anterior al documento; si no lo hubo, el origen del primero posterior; y
-- sin cambios, el entorno de hoy.
UPDATE documento d SET entorno = COALESCE(
    (SELECT substring(a.detalle FROM 'hacia=([A-Z]+)') FROM auditoria_admin a
     WHERE a.accion = 'CAMBIAR_ENTORNO_EMPRESA' AND a.tenant_id = d.tenant_id AND a.ocurrido_en <= d.created_at
     ORDER BY a.ocurrido_en DESC LIMIT 1),
    (SELECT substring(a.detalle FROM 'desde=([A-Z]+)') FROM auditoria_admin a
     WHERE a.accion = 'CAMBIAR_ENTORNO_EMPRESA' AND a.tenant_id = d.tenant_id AND a.ocurrido_en > d.created_at
     ORDER BY a.ocurrido_en ASC LIMIT 1),
    (SELECT t.entorno FROM tenant t WHERE t.id = d.tenant_id));

ALTER TABLE documento ALTER COLUMN entorno SET NOT NULL;
ALTER TABLE documento ADD CONSTRAINT ck_documento_entorno CHECK (entorno IN ('BETA', 'PRODUCCION'));

-- Todo documento nuevo toma el entorno que su empresa tiene al guardarlo, sea cual sea el camino (la emisión, un script, una prueba): «cuenta solo Producción»
-- no depende de que cada INSERT se acuerde. El cambio de entorno exige que la empresa no tenga envíos pendientes, así que un documento no se envía después a
-- otro entorno que el suyo. Quien ya trae el entorno lo conserva.
CREATE FUNCTION documento_toma_el_entorno() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.entorno IS NULL THEN
        SELECT entorno INTO NEW.entorno FROM tenant WHERE id = NEW.tenant_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_documento_toma_el_entorno BEFORE INSERT ON documento FOR EACH ROW EXECUTE FUNCTION documento_toma_el_entorno();
