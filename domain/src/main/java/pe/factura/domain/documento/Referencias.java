package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.List;

/**
 * Documentos que la factura referencia (bloque "Documentos relacionados" de la hoja Factura2_0): la orden de compra o de
 * servicio del cliente (cac:OrderReference, campo 59), las guías de remisión del traslado y otros documentos del catálogo
 * 12. Ninguno cambia importes; SUNAT solo valida formato y que no se repitan (2364, 2365).
 */
public record Referencias(String ordenCompra, List<GuiaRelacionada> guias, List<DocumentoRelacionado> otros) {

    public Referencias {
        guias = guias == null ? List.of() : List.copyOf(guias);
        otros = otros == null ? List.of() : List.copyOf(otros);
        if (ordenCompra != null && (ordenCompra.isEmpty() || ordenCompra.length() > 20 || ordenCompra.chars().anyMatch(Character::isISOControl)))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4233 - La orden de compra debe tener de 1 a 20 caracteres, sin saltos de línea ni tabuladores");
        if (guias.stream().distinct().count() < guias.size())
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "2364 - La misma guía de remisión aparece más de una vez");
        if (otros.stream().distinct().count() < otros.size())
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "2365 - El mismo documento relacionado aparece más de una vez");
    }

    public static Referencias ninguna() { return new Referencias(null, List.of(), List.of()); }

    public boolean vacias() { return ordenCompra == null && guias.isEmpty() && otros.isEmpty(); }
}
