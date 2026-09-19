package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Locale;
import java.util.Set;

/**
 * Datos propios de una factura de exportación (tipos de operación 0200–0208 del catálogo 51, afectación 40, tributo 9995):
 * el Incoterm de la venta ({@code cac:DeliveryTerms/cbc:ID}, sin validación SUNAT pero exigido por Aduanas en la DAM) y, en
 * la exportación de servicios 0201/0208, el país donde se usa o aprovecha el servicio ({@code cac:Delivery/cac:DeliveryLocation/
 * cac:Address/cac:Country/cbc:IdentificationCode}, reglas 3098/3099: obligatorio, del catálogo 04 y distinto de PE).
 */
public record Exportacion(String incoterm, String paisUso) {
    public static final Set<String> TIPOS_OPERACION = Set.of("0200", "0201", "0202", "0203", "0204", "0205", "0206", "0207", "0208");
    /** Exigen los datos del huésped por línea (catálogo 55: 4000–4009, reglas 3136–3145): no soportados todavía. */
    public static final Set<String> CON_HUESPED = Set.of("0202", "0205");
    /** Exportación de servicios usados en el extranjero: llevan el país de uso (3098). */
    public static final Set<String> EXIGEN_PAIS_USO = Set.of("0201", "0208");
    /** Con receptor con RUC (tipo 6) SUNAT rechaza (2800) salvo que lleve la leyenda 2008. */
    public static final Set<String> SIN_RUC = Set.of("0200", "0201", "0204");
    /** Incoterms 2020 (ICC). */
    public static final Set<String> INCOTERMS = Set.of("EXW", "FCA", "FAS", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP");
    private static final Set<String> PAISES = Set.of(Locale.getISOCountries());

    public Exportacion {
        incoterm = incoterm == null ? null : incoterm.strip().toUpperCase(Locale.ROOT);
        paisUso = paisUso == null ? null : paisUso.strip().toUpperCase(Locale.ROOT);
        if (incoterm != null && !INCOTERMS.contains(incoterm))
            throw new DomainException("EXPORTACION_INVALIDA", "El incoterm " + incoterm + " no es uno de los Incoterms 2020: " + String.join(", ", INCOTERMS.stream().sorted().toList()));
        if (paisUso != null && (!PAISES.contains(paisUso) || "PE".equals(paisUso)))
            throw new DomainException("EXPORTACION_INVALIDA", "3099 - El país de uso del servicio debe ser un código ISO 3166-1 (catálogo 04) distinto de PE: " + paisUso);
    }

    public static boolean es(String tipoOperacion) { return tipoOperacion != null && TIPOS_OPERACION.contains(tipoOperacion); }

    public static boolean paisValido(String pais) { return pais != null && PAISES.contains(pais.strip().toUpperCase(Locale.ROOT)); }

    /** Lo que exige el tipo de operación: país de uso en 0201/0208; el huésped (0202/0205) aún no se soporta. */
    static void validar(Exportacion e, String tipoOperacion) {
        if (!es(tipoOperacion)) {
            if (e != null) throw new DomainException("EXPORTACION_INVALIDA", "Los datos de exportación (incoterm, pais_uso) solo aplican a los tipos de operación 0200–0208");
            return;
        }
        if (CON_HUESPED.contains(tipoOperacion))
            throw new DomainException("TIPO_OPERACION_INVALIDO", "El tipo de operación " + tipoOperacion + " (hospedaje o paquete turístico a no domiciliados) exige los datos del huésped por línea (catálogo 55), aún no soportados");
        if (EXIGEN_PAIS_USO.contains(tipoOperacion) && (e == null || e.paisUso() == null))
            throw new DomainException("EXPORTACION_INVALIDA", "3098 - La exportación de servicios " + tipoOperacion + " exige exportacion.pais_uso (país donde se usa o aprovecha el servicio)");
        if (!EXIGEN_PAIS_USO.contains(tipoOperacion) && e != null && e.paisUso() != null)
            throw new DomainException("EXPORTACION_INVALIDA", "4041 - exportacion.pais_uso solo aplica a la exportación de servicios 0201 y 0208");
    }
}
