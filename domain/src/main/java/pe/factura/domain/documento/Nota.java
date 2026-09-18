package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

/**
 * Lo que distingue una nota de crédito (07) o de débito (08) de una factura: el comprobante que modifica
 * (cac:BillingReference) y el motivo del catálogo 09 (NC) o 10 (ND) con su sustento (cac:DiscrepancyResponse).
 * Solo se modifican facturas (01); las notas sobre boletas viajan en el resumen diario (#20).
 */
public record Nota(TipoDocumento tipoAfectado, String serieAfectada, long numeroAfectado, String motivo, String descripcion) {

    /** Motivo 13 de la NC: corrige el monto neto pendiente o las cuotas de una factura al crédito (reglas 3257, 3259, 3260). */
    public static final String MOTIVO_NC_CUOTAS = "13";

    public Nota {
        if (tipoAfectado != TipoDocumento.FACTURA)
            throw new DomainException("NOTA_INVALIDA", "2116 - Solo se emiten notas sobre facturas (01); las de boletas van en el resumen diario");
        if (!TipoDocumento.FACTURA.serieValida(serieAfectada))
            throw new DomainException("NOTA_INVALIDA", "2117 - La serie del documento que modifica no es de factura: " + serieAfectada);
        if (numeroAfectado <= 0)
            throw new DomainException("NOTA_INVALIDA", "2117 - El número del documento que modifica debe ser mayor que cero");
        if (motivo == null || motivo.isBlank())
            throw new DomainException("NOTA_INVALIDA", "2128 - Debe indicar el motivo de la nota (catálogo 09 o 10)");
        if (descripcion == null || descripcion.isBlank() || descripcion.length() > 500 || descripcion.chars().anyMatch(Character::isISOControl))
            throw new DomainException("NOTA_INVALIDA", "2135 - El sustento de la nota debe tener de 1 a 500 caracteres, sin saltos de línea ni tabuladores");
    }

    /** Serie-número del comprobante modificado, tal como va en cbc:ReferenceID y cac:BillingReference. */
    public String documentoAfectado() { return serieAfectada + "-" + numeroAfectado; }

    /** Regla 2172: el motivo debe existir en el catálogo 09 (NC) o 10 (ND) según el tipo de nota. */
    void validarMotivoPara(TipoDocumento tipoNota) {
        String catalogo = tipoNota == TipoDocumento.NOTA_CREDITO ? "09" : "10";
        if (CatalogoSunat.porId(catalogo).flatMap(c -> c.entrada(motivo)).isEmpty())
            throw new DomainException("NOTA_INVALIDA", "2172 - El motivo " + motivo + " no existe en el catálogo " + catalogo + " (" + tipoNota + ")");
    }

    /** Descripción oficial del motivo (catálogo 09/10) para respuestas y representación impresa. */
    public String descripcionMotivo(TipoDocumento tipoNota) {
        String catalogo = tipoNota == TipoDocumento.NOTA_CREDITO ? "09" : "10";
        return CatalogoSunat.porId(catalogo).flatMap(c -> c.entrada(motivo)).map(CatalogoSunat.Entrada::descripcion).orElse(motivo);
    }

    /** Solo la nota de crédito 13 corrige cuotas; en la nota de débito el 13 es "Penalidades" (catálogo 10) y lleva ítems como cualquier otra. */
    public boolean corrigeCuotas(TipoDocumento tipoNota) { return tipoNota == TipoDocumento.NOTA_CREDITO && MOTIVO_NC_CUOTAS.equals(motivo); }

    /**
     * Única línea de una NC 13: SUNAT exige importe total cero (regla 3315) y acepta una línea gravada de valor 0 con la
     * descripción del sustento (comprobado en e-beta, #32); los ítems que envíe el emisor se ignoran.
     */
    Item lineaSinImporte() {
        return new Item(null, descripcion, "ZZ", java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO.setScale(2), TipoAfectacionIgv.GRAVADO);
    }
}
