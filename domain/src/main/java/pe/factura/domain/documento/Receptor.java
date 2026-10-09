package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Ruc;

import java.util.Locale;
import java.util.Set;

/**
 * Adquirente. Se valida al emitir ({@link #exigirValidoParaFactura(String)}), no al rehidratar: los comprobantes ya
 * emitidos no se tocan. {@code pais}: código ISO 3166-1 (catálogo 04) del domicilio del receptor, que en una factura de
 * exportación identifica al cliente del exterior ({@code cac:RegistrationAddress/cac:Country}); en las demás es opcional.
 */
public record Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion, String pais) {
    /** Documentos del catálogo 06 que puede llevar el receptor de una exportación (regla 2800): no domiciliados, DNI, CE, pasaporte… */
    static final Set<String> DOCUMENTOS_EXPORTACION = Set.of("0", "1", "4", "6", "7", "A", "B", "C", "D", "E", "G");

    public Receptor {
        pais = pais == null || pais.isBlank() ? null : pais.strip().toUpperCase(Locale.ROOT);
    }

    public Receptor(String tipoDoc, String numDoc, String razonSocial, String direccion) { this(tipoDoc, numDoc, razonSocial, direccion, null); }

    public boolean esRuc() { return "6".equals(tipoDoc) && numDoc != null && numDoc.matches("\\d{11}"); }

    /** Tipo y número de documento de un comprador sin identificar en una boleta de hasta S/ 700 (guía del resumen diario, campos 12 y 13). */
    public static final String SIN_DOCUMENTO = "-";

    /** Catálogo 06 completo: lo que admite el receptor de una boleta además de {@link #SIN_DOCUMENTO}. */
    static final Set<String> DOCUMENTOS_BOLETA = Set.of("0", "1", "4", "6", "7", "A", "B", "C", "D", "E", "F", "G", "H");

    public boolean sinDocumento() { return SIN_DOCUMENTO.equals(tipoDoc); }

    void exigirValidoParaFactura() { exigirValidoParaFactura("0101"); }

    /**
     * Venta interna: RUC con dígito verificador (2017). Exportación (0200–0208): documento del catálogo 06 con su formato
     * (2800–2802), país del receptor, y sin RUC en 0200/0201/0204 (2800). Razón social de 3 a 1500 caracteres sin saltos
     * de línea (2021/2022) en ambos casos.
     */
    void exigirValidoParaFactura(String tipoOperacion) {
        if (Exportacion.es(tipoOperacion)) exigirValidoParaExportacion(tipoOperacion);
        else {
            if (!esRuc()) throw new DomainException("RECEPTOR_INVALIDO", "2017 - La factura requiere un receptor con RUC (tipo_doc 6, 11 dígitos)");
            Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2017 - Receptor");
        }
        exigirPaisYNombre();
    }

    /**
     * Boleta (Boleta2_0): DNI de 8 dígitos (4207), RUC con dígito verificador (2017), los demás documentos del catálogo 06 hasta 15 caracteres sin espacios (4208), o
     * {@link #SIN_DOCUMENTO} con número «-» (2802). SUNAT deja 4207/4208 como observación; aquí son error para no emitir un comprador mal identificado. El tope de
     * S/ 700 sin documento depende del total y lo aplica la boleta.
     */
    void exigirValidoParaBoleta() {
        if (sinDocumento()) {
            if (!SIN_DOCUMENTO.equals(numDoc)) throw new DomainException("RECEPTOR_INVALIDO", "2802 - Sin documento (tipo_doc «-»), el número de documento también es «-»");
        } else if (tipoDoc == null || !DOCUMENTOS_BOLETA.contains(tipoDoc)) {
            throw new DomainException("RECEPTOR_INVALIDO", "2016 - El tipo de documento del comprador es del catálogo 06 (1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte…) o «-» sin documento; recibido " + tipoDoc);
        } else if ("6".equals(tipoDoc)) {
            Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2017 - Receptor");
        } else if ("1".equals(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("\\d{8}")) throw new DomainException("RECEPTOR_INVALIDO", "4207 - El DNI del comprador tiene 8 dígitos");
        } else if (numDoc == null || !numDoc.matches("\\S{1,15}")) {
            throw new DomainException("RECEPTOR_INVALIDO", "4208 - El número de documento del comprador tiene hasta 15 caracteres sin espacios");
        }
        exigirPaisYNombre();
    }

    private void exigirPaisYNombre() {
        if (pais != null && !Exportacion.paisValido(pais))
            throw new DomainException("RECEPTOR_INVALIDO", "El país del receptor debe ser un código ISO 3166-1 de dos letras (catálogo 04): " + pais);
        if (razonSocial == null || razonSocial.strip().length() < 3 || razonSocial.strip().length() > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RECEPTOR_INVALIDO", "2022 - La razón social del receptor tiene de 3 a 1500 caracteres, sin saltos de línea");
    }

    private void exigirValidoParaExportacion(String tipoOperacion) {
        if (tipoDoc == null || !DOCUMENTOS_EXPORTACION.contains(tipoDoc))
            throw new DomainException("RECEPTOR_INVALIDO", "2800 - En una exportación el receptor lleva un documento del catálogo 06: 0 (no domiciliado sin RUC), 1, 4, 7, A, B, C, D, E o G; recibido " + tipoDoc);
        if ("6".equals(tipoDoc)) {
            if (Exportacion.SIN_RUC.contains(tipoOperacion))
                throw new DomainException("RECEPTOR_INVALIDO", "2800 - La exportación " + tipoOperacion + " no admite un receptor con RUC (tipo_doc 6)");
            Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2017 - Receptor");
        } else if ("1".equals(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("\\d{8}")) throw new DomainException("RECEPTOR_INVALIDO", "2801 - El DNI del receptor tiene 8 dígitos");
        } else if (numDoc == null || !numDoc.matches("\\S{1,15}")) {
            throw new DomainException("RECEPTOR_INVALIDO", "2802 - El número de documento del receptor tiene hasta 15 caracteres sin espacios");
        }
        if (pais == null) throw new DomainException("RECEPTOR_INVALIDO", "En una exportación el receptor lleva el país (cliente.pais, código ISO 3166-1 del catálogo 04)");
    }
}
