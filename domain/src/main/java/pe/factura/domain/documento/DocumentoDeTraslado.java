package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;
import pe.factura.domain.tenant.Ruc;

import java.util.Set;

/**
 * Documento relacionado con el traslado ({@code cac:AdditionalDocumentReference}): tipo del catálogo 61, número y, en los comprobantes, el RUC de quien
 * lo emitió. Es el sustento del traslado: la factura de la venta, la guía remitente que lleva el transportista, la DAM, una autorización…
 */
public record DocumentoDeTraslado(String tipo, String numero, String rucEmisor) {
    /** Los del catálogo 61 que son comprobantes o guías electrónicas: se identifican por serie-número y por el RUC de quien los emitió. */
    static final Set<String> COMPROBANTES = Set.of("01", "03", "04", "09", "12", "31", "48");

    public DocumentoDeTraslado {
        tipo = TextoDeGuia.limpio(tipo);
        numero = numero == null ? null : numero.strip().toUpperCase(java.util.Locale.ROOT);
        rucEmisor = TextoDeGuia.limpio(rucEmisor);
    }

    public boolean esComprobante() { return COMPROBANTES.contains(tipo); }

    /** Catálogo 61, la columna «GRE aplicable»: cada tipo vale en la remitente, en la transportista o en las dos. */
    void exigirValido(TipoDocumento guia) {
        CatalogoSunat.Entrada e = tipo == null ? null : CatalogoSunat.porId("61").flatMap(c -> c.entrada(tipo)).orElse(null);
        if (e == null) throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "El tipo de documento relacionado es del catálogo 61 (01 factura, 09 guía remitente, 50 DAM…); recibido " + tipo);
        String aplica = e.extra().getOrDefault("GRE aplicable", "");
        boolean remitente = guia == TipoDocumento.GUIA_REMITENTE;
        if (remitente ? aplica.startsWith("solo transportista") : aplica.startsWith("solo remitente"))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "3445 - El documento relacionado " + tipo + " (" + e.descripcion() + ") es " + aplica + " (catálogo 61): no va en una guía "
                    + (remitente ? "remitente" : "transportista"));
        if (esComprobante() ? numero == null || !numero.matches("[A-Z0-9]{4}-\\d{1,8}") : !TextoDeGuia.unaLinea(numero, 1, 100))
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "3441 - El número del documento relacionado " + tipo + " no cumple el formato"
                    + (esComprobante() ? " serie-número (F001-123, T001-45)" : " (hasta 100 caracteres, sin saltos de línea)") + "; recibido " + numero);
        if (esComprobante() && rucEmisor == null)
            throw new DomainException("DOCUMENTO_RELACIONADO_INVALIDO", "Indique el RUC de quien emitió el documento relacionado " + tipo + " " + numero);
        if (rucEmisor != null) Ruc.exigirValido(rucEmisor, "DOCUMENTO_RELACIONADO_INVALIDO", "El RUC del emisor del documento relacionado " + numero);
    }

    /** El mismo documento (regla 3340). */
    boolean mismoQue(DocumentoDeTraslado otro) { return tipo.equals(otro.tipo) && numero.equals(otro.numero) && java.util.Objects.equals(rucEmisor, otro.rucEmisor); }

    public String descripcionTipo() { return CatalogoSunat.descripcion("61", tipo).orElse(tipo); }
}
