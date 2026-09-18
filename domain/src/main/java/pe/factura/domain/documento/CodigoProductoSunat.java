package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

/**
 * Código de producto SUNAT (catálogo 25, UNSPSC v14) de un ítem: cac:CommodityClassification/cbc:ItemClassificationCode.
 * SUNAT rechaza el formato (8 dígitos, distinto de 00000000 y 99999999: regla 3496) y solo observa los que no están en su
 * listado (4332) o no llegan al tercer nivel jerárquico (4337), así que aquí se valida únicamente el formato: el catálogo
 * completo tiene decenas de miles de códigos y no se embebe; {@code GET /v1/catalogos/25} expone los listados 25.1–25.3.
 */
public record CodigoProductoSunat(String codigo) {

    public CodigoProductoSunat {
        if (codigo == null || !codigo.matches("[0-9]{8}") || "00000000".equals(codigo) || "99999999".equals(codigo))
            throw new DomainException("ITEM_INVALIDO", "3496 - El código de producto SUNAT debe tener 8 dígitos (UNSPSC, catálogo 25): " + codigo);
    }

    /** {@code null} si no se informó; así el DTO y la persistencia no repiten el chequeo de nulo. */
    public static CodigoProductoSunat de(String codigo) { return codigo == null ? null : new CodigoProductoSunat(codigo); }

    @Override public String toString() { return codigo; }
}
