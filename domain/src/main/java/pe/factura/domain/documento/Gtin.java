package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Map;

/**
 * Código de producto GTIN (GS1) de un ítem (campo 29 de la hoja Factura2_0): cac:StandardItemIdentification/cbc:ID con
 * {@code schemeID} = tipo de estructura, cuya longitud debe coincidir con el código (reglas 4333–4335).
 */
public record Gtin(String tipo, String codigo) {

    public static final Map<String, Integer> LONGITUD = Map.of("GTIN-8", 8, "GTIN-12", 12, "GTIN-13", 13, "GTIN-14", 14);

    public Gtin {
        if (tipo == null || !LONGITUD.containsKey(tipo))
            throw new DomainException("ITEM_INVALIDO", "4335 - El tipo de GTIN debe ser GTIN-8, GTIN-12, GTIN-13 o GTIN-14");
        if (codigo == null || !codigo.matches("[0-9]+") || codigo.length() != LONGITUD.get(tipo))
            throw new DomainException("ITEM_INVALIDO", "4334 - El código " + codigo + " no es un " + tipo + " válido (" + LONGITUD.get(tipo) + " dígitos)");
    }
}
