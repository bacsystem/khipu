package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guía de remisión que sustenta el traslado de los bienes facturados (cac:DespatchDocumentReference, campo 22 de la hoja
 * Factura2_0): {@code 09} guía del remitente o {@code 31} guía del transportista (catálogo 01), con la numeración
 * serie-número que SUNAT admite para guías electrónicas, físicas y de series antiguas (regla 4006).
 */
public record GuiaRelacionada(String tipo, String numero) {

    public static final Set<String> TIPOS = Set.of("09", "31");
    private static final Pattern NUMERO = Pattern.compile("T[A-Z0-9]{3}-[0-9]{1,8}|[0-9]{4}-[0-9]{1,8}|EG0[1-4]-[0-9]{1,8}|[EG07]{4}-[0-9]{1,8}|G[0-9]{3}-[0-9]{1,8}|V[A-Z0-9]{3}-[0-9]{1,8}");

    public GuiaRelacionada {
        if (tipo == null || !TIPOS.contains(tipo))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4005 - El tipo de guía relacionada debe ser 09 (remitente) o 31 (transportista)");
        if (numero == null || !NUMERO.matcher(numero).matches())
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "4006 - El número de guía " + numero + " no tiene el formato serie-número esperado (p. ej. T001-123, EG01-45)");
    }
}
