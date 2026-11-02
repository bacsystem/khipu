package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;

/**
 * Bien que se traslada ({@code cac:DespatchLine}): descripción, unidad del catálogo 03 y cantidad, más el código del emisor y el código de producto SUNAT
 * (UNSPSC, 8 dígitos) opcionales. La guía no lleva precios.
 */
public record ItemDeGuia(String codigo, String descripcion, String unidad, BigDecimal cantidad, String codigoSunat) {
    public ItemDeGuia {
        codigo = TextoDeGuia.limpio(codigo);
        descripcion = TextoDeGuia.limpio(descripcion);
        unidad = unidad == null ? null : unidad.strip().toUpperCase(java.util.Locale.ROOT);
        codigoSunat = TextoDeGuia.limpio(codigoSunat);
    }

    void exigirValido(int linea) {
        String l = "El ítem " + linea;
        if (!TextoDeGuia.unaLinea(descripcion, 1, 500)) throw new DomainException("ITEM_INVALIDO", l + ": la descripción tiene de 1 a 500 caracteres, sin saltos de línea");
        if (unidad == null || !CatalogoSunat.porId("03").map(c -> c.contiene(unidad)).orElse(false))
            throw new DomainException("ITEM_INVALIDO", l + ": la unidad es un código del catálogo 03 (NIU, KGM, BX…); recibido " + unidad);
        if (!TextoDeGuia.positivo(cantidad, 12, 10)) throw new DomainException("ITEM_INVALIDO", l + ": la cantidad es mayor que cero, con hasta 12 enteros y 10 decimales");
        if (codigo != null && !TextoDeGuia.unaLinea(codigo, 1, 30)) throw new DomainException("ITEM_INVALIDO", l + ": el código tiene hasta 30 caracteres, sin saltos de línea");
        if (codigoSunat != null && !codigoSunat.matches("\\d{8}")) throw new DomainException("ITEM_INVALIDO", l + ": el código de producto SUNAT son 8 dígitos (UNSPSC, catálogo 25)");
    }
}
