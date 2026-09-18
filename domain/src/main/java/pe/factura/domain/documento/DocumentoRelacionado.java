package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Set;

/**
 * Otro documento que sustenta la operación (cac:AdditionalDocumentReference, campo 23 de la hoja Factura2_0): tipo del
 * catálogo 12 y número de hasta 30 caracteres sin espacios (regla 4010). Los tipos 01–03 y 10 no se admiten aquí: 02/03
 * son las facturas y boletas de anticipo, que van en {@link Anticipo}; 01 y 10 no los acepta SUNAT (regla 4009).
 */
public record DocumentoRelacionado(String tipo, String numero) {

    public static final Set<String> TIPOS = Set.of("04", "05", "06", "07", "08", "09", "99");

    public DocumentoRelacionado {
        if (tipo == null || !TIPOS.contains(tipo))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4009 - El tipo de documento relacionado debe ser 04–09 o 99 (catálogo 12); los anticipos van en `anticipos`");
        if (numero == null || numero.isEmpty() || numero.length() > 30 || numero.chars().anyMatch(ch -> Character.isWhitespace(ch) || Character.isISOControl(ch)))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4010 - El número del documento relacionado debe tener de 1 a 30 caracteres sin espacios");
    }
}
