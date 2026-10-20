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

    /**
     * Nombre convenido para el comprador {@link #SIN_DOCUMENTO} (#303): el nodo del adquirente va siempre (2014/2015) y su nombre tiene al
     * menos 3 caracteres (2022, error desde 2027-01-01 en Boleta2_0 y en el resumen diario). El portal lo propone al elegir «sin documento».
     */
    public static final String NOMBRE_SIN_DOCUMENTO = "CLIENTES VARIOS";

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
        exigirPaisYNombre("La razón social del receptor");
    }

    /**
     * Boleta (Boleta2_0): DNI de 8 dígitos (4207), RUC con dígito verificador (2017), los demás documentos del catálogo 06 hasta 15 caracteres sin espacios (4208), o
     * {@link #SIN_DOCUMENTO} con número «-». SUNAT deja 4207/4208 como observación; aquí son error para no emitir un comprador mal identificado. El tope de
     * S/ 700 sin documento depende del total y lo aplica la boleta.
     */
    void exigirValidoParaBoleta() {
        if (sinDocumento()) {
            // Sin código SUNAT: Boleta2_0 no tiene regla propia para el comprador sin documento (2802 es de exportación en Factura2_0).
            if (!SIN_DOCUMENTO.equals(numDoc)) throw new DomainException("RECEPTOR_INVALIDO", "Sin documento (tipo_doc «-»), el número de documento también es «-»");
        } else if (tipoDoc == null || !DOCUMENTOS_BOLETA.contains(tipoDoc)) {
            throw new DomainException("RECEPTOR_INVALIDO", "El tipo de documento del comprador es del catálogo 06 (1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte…) o «-» sin documento; recibido " + tipoDoc);
        } else if ("6".equals(tipoDoc)) {
            Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2017 - Receptor");
        } else if ("1".equals(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("\\d{8}")) throw new DomainException("RECEPTOR_INVALIDO", "4207 - El DNI del comprador tiene 8 dígitos");
        } else if (numDoc == null || !numDoc.matches("\\S{1,15}")) {
            throw new DomainException("RECEPTOR_INVALIDO", "4208 - El número de documento del comprador tiene hasta 15 caracteres sin espacios");
        }
        exigirPaisYNombre("El nombre del comprador");
    }

    /**
     * Boleta2_0 fila 93 (2800): en exportación (0200–0208) y a no domiciliados (0401) el comprador no lleva RUC. A diferencia de la factura, que en esos tipos acepta cualquier
     * documento del catálogo 06 y, en 0401, exige RUC. e-beta (2026-10-18): rechazó con 2800 el RUC en 0200 y en 0401 y aceptó el pasaporte, el DNI y «sin documento».
     */
    void exigirSinRucEnExportacionOEnNoDomiciliado(String tipoOperacion) {
        if ("6".equals(tipoDoc) && (Exportacion.es(tipoOperacion) || NO_DOMICILIADO.equals(tipoOperacion)))
            throw new DomainException("RECEPTOR_INVALIDO", "2800 - En una boleta con tipo de operación " + tipoOperacion + " el comprador no lleva RUC (tipo_doc 6): use su documento del exterior (0, 4, 7…), su DNI o, hasta S/ 700, «sin documento»");
    }

    /** Documentos del vendedor de una liquidación de compra (catálogo 06, parámetro 006 de la regla 2800): DNI, carné de extranjería y pasaporte. */
    static final Set<String> DOCUMENTOS_VENDEDOR = Set.of("1", "4", "7");

    /**
     * El vendedor de una liquidación de compra (#327, LiquidacionCompra2_0 filas 63–79): una persona sin RUC, con DNI de 8 dígitos (1), carné de extranjería o pasaporte (4, 7: hasta
     * 15 caracteres sin espacios, 2802); quien tiene RUC emite su propia factura o boleta, así que el 6 no se admite (2800). Su nombre (2021/2022: hasta 1500 caracteres sin
     * saltos de línea) y su domicilio, obligatorio aquí y no en la factura: de 3 a 250 caracteres (2452, 2593). El país, si se informa, es PE (4041). Que el DNI exista en el
     * padrón, no sea de un menor ni de un fallecido (2450, 2460–2462) lo cruza SUNAT con datos que khipu no tiene.
     */
    void exigirValidoComoVendedor() {
        if (tipoDoc == null || !DOCUMENTOS_VENDEDOR.contains(tipoDoc))
            throw new DomainException("RECEPTOR_INVALIDO", "2800 - El vendedor de una liquidación de compra se identifica con DNI (1), carné de extranjería (4) o pasaporte (7); recibido "
                    + tipoDoc + ("6".equals(tipoDoc) ? ": quien tiene RUC emite su propia factura o boleta" : ""));
        if ("1".equals(tipoDoc)) {
            if (numDoc == null || !numDoc.matches("\\d{8}")) throw new DomainException("RECEPTOR_INVALIDO", "2450 - El DNI del vendedor tiene 8 dígitos");
        } else if (numDoc == null || !numDoc.matches("\\S{1,15}")) {
            throw new DomainException("RECEPTOR_INVALIDO", "2802 - El número de documento del vendedor tiene hasta 15 caracteres sin espacios");
        }
        if (razonSocial == null || razonSocial.isBlank())
            throw new DomainException("RECEPTOR_INVALIDO", "2021 - Falta el nombre del vendedor");
        if (razonSocial.length() > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RECEPTOR_INVALIDO", "2022 - El nombre del vendedor tiene hasta 1500 caracteres, sin saltos de línea");
        LiquidacionCompra.exigirDireccion(direccion == null ? null : direccion.strip(), "2452", "2593", "del domicilio del vendedor", "RECEPTOR_INVALIDO");
        if (pais != null && !"PE".equals(pais))
            throw new DomainException("RECEPTOR_INVALIDO", "4041 - El domicilio del vendedor de una liquidación de compra está en el Perú (país PE); recibido " + pais);
    }

    /**
     * El proveedor de un comprobante de retención (#40, Retenciones1_0 filas 34–51): siempre con RUC (2511: tipo 6; 2724: 11 dígitos; khipu exige además el dígito verificador), distinto
     * del agente (2620), con su razón social (2134; hasta 1500 caracteres sin saltos de línea, 2133). La dirección, si va, cabe en el StreetName (hasta 100 caracteres en una línea:
     * 2918 la observaría) y el país, si se informa, es PE (2548). Que el RUC exista (2621) lo valida el padrón al emitir.
     */
    void exigirValidoComoProveedor(String rucAgente) {
        if (!"6".equals(tipoDoc))
            throw new DomainException("RECEPTOR_INVALIDO", "2511 - El proveedor de un comprobante de retención se identifica con su RUC (tipo_doc 6); recibido " + tipoDoc);
        if (numDoc == null || !numDoc.matches("\\d{11}"))
            throw new DomainException("RECEPTOR_INVALIDO", "2724 - El RUC del proveedor tiene 11 dígitos");
        Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2724 - Proveedor");
        if (numDoc.equals(rucAgente))
            throw new DomainException("RECEPTOR_INVALIDO", "2620 - El proveedor no puede ser la misma empresa que retiene (" + rucAgente + ")");
        exigirContraparteDeAgente("proveedor", "2918");
    }

    /**
     * El cliente de un comprobante de percepción (#40, Percepciones1_0 filas 35–54): documento del catálogo 06 (2511; el RUC con dígito verificador, el DNI de 8 dígitos y los demás hasta
     * 15 caracteres sin espacios, 2680), distinto del agente (2604), con su nombre (2134/2133). Que el RUC exista (2605) lo valida el padrón al emitir.
     */
    void exigirValidoComoClienteDePercepcion(String rucAgente) {
        if (tipoDoc == null || !DOCUMENTOS_BOLETA.contains(tipoDoc))
            throw new DomainException("RECEPTOR_INVALIDO", "2511 - El tipo de documento del cliente es del catálogo 06 (1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte…); recibido " + tipoDoc);
        if ("6".equals(tipoDoc)) Ruc.exigirValido(numDoc, "RECEPTOR_INVALIDO", "2680 - Cliente");
        else if ("1".equals(tipoDoc) && (numDoc == null || !numDoc.matches("\\d{8}")))
            throw new DomainException("RECEPTOR_INVALIDO", "2680 - El DNI del cliente tiene 8 dígitos");
        else if (numDoc == null || !numDoc.matches("[A-Za-z0-9]{1,15}"))
            throw new DomainException("RECEPTOR_INVALIDO", "2680 - El número de documento del cliente es alfanumérico de hasta 15 caracteres");
        if (numDoc.equals(rucAgente))
            throw new DomainException("RECEPTOR_INVALIDO", "2604 - El cliente no puede ser la misma empresa que percibe (" + rucAgente + ")");
        exigirContraparteDeAgente("cliente", "2919");
    }

    private void exigirContraparteDeAgente(String quien, String reglaDireccion) {
        if (razonSocial == null || razonSocial.isBlank())
            throw new DomainException("RECEPTOR_INVALIDO", "2134 - Falta la razón social o el nombre del " + quien);
        if (razonSocial.strip().length() > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RECEPTOR_INVALIDO", "2133 - La razón social del " + quien + " tiene hasta 1500 caracteres, sin saltos de línea ni tabuladores");
        if (direccion != null && (direccion.strip().length() > 100 || direccion.chars().anyMatch(Character::isISOControl)))
            throw new DomainException("RECEPTOR_INVALIDO", reglaDireccion + " - La dirección del " + quien + " tiene hasta 100 caracteres, en una sola línea");
        if (pais != null && !"PE".equals(pais))
            throw new DomainException("RECEPTOR_INVALIDO", "2548 - El domicilio del " + quien + " está en el Perú (país PE); recibido " + pais);
    }

    /** Ventas a no domiciliados que no califican como exportación (catálogo 51). */
    static final String NO_DOMICILIADO = "0401";

    /**
     * 2021/2022 se cuentan sin los espacios de los extremos: «  JP  » es un nombre de 2 caracteres aunque SUNAT llegara a contar los espacios,
     * así que nunca se queda corto frente a ella. Solo al emitir: lo ya guardado se rehidrata tal cual (#303).
     */
    private void exigirPaisYNombre(String queNombre) {
        if (pais != null && !Exportacion.paisValido(pais))
            throw new DomainException("RECEPTOR_INVALIDO", "El país del receptor debe ser un código ISO 3166-1 de dos letras (catálogo 04): " + pais);
        int largo = razonSocial == null ? 0 : largoSinExtremos(razonSocial);
        if (largo < 3 || largo > 1500 || razonSocial.chars().anyMatch(Character::isISOControl))
            throw new DomainException("RECEPTOR_INVALIDO", "2022 - " + queNombre + " tiene de 3 a 1500 caracteres, sin saltos de línea");
    }

    /**
     * Largo sin los espacios de los extremos, con los mismos espacios que quita el portal (`trim` de JS): además de los de {@link String#strip()},
     * los de no separación (U+00A0, U+2007, U+202F) y el BOM. Con `strip()` solo, «JP» + U+00A0 —o tres U+00A0— pasaba la 2022 (#347-H1).
     */
    private static int largoSinExtremos(String s) {
        int inicio = 0, fin = s.length();
        while (inicio < fin && esEspacio(s.charAt(inicio))) inicio++;
        while (fin > inicio && esEspacio(s.charAt(fin - 1))) fin--;
        return fin - inicio;
    }

    private static boolean esEspacio(char c) { return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿'; }

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
