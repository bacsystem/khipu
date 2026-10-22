package pe.factura.adapters.sunat;

import pe.factura.domain.tenant.Entorno;

import java.util.regex.Pattern;

/**
 * Las URLs de {@code billService} por entorno. SUNAT publica dos servicios de envío: el de facturas, boletas, notas y liquidaciones de compra ({@code ol-ti-itcpfegem}) y el de
 * retenciones y percepciones ({@code ol-ti-itemision-otroscpe-gem}, #40), que recibe además sus resúmenes de reversiones (RR). El de facturas rechaza un 20 o un 40 («no es un cpe
 * valido», e-beta 2026-10-20): el nombre del archivo decide a cuál va.
 */
public record SunatUrls(String beta, String produccion, String otrosBeta, String otrosProduccion) {
    /** {@code RUC-20-…}, {@code RUC-40-…} o {@code RUC-RR-…}: lo que va al servicio de retenciones y percepciones. */
    private static final Pattern DE_OTROS_CPE = Pattern.compile("\\d{11}-(20|40|RR)-.*");

    public SunatUrls(String beta, String produccion) { this(beta, produccion, null, null); }

    public String para(Entorno e) { return e == Entorno.PRODUCCION ? produccion : beta; }

    /** La del servicio que recibe ese archivo (sin extensión), en el entorno {@code e}. */
    public String para(Entorno e, String nombreArchivo) {
        if (nombreArchivo == null || !DE_OTROS_CPE.matcher(nombreArchivo).matches()) return para(e);
        String url = e == Entorno.PRODUCCION ? otrosProduccion : otrosBeta;
        if (url == null || url.isBlank())
            throw new IllegalStateException("No hay URL del servicio de retenciones y percepciones de SUNAT para " + e + " (app.sunat.otros-" + (e == Entorno.PRODUCCION ? "prod" : "beta") + "-url)");
        return url;
    }

    public static boolean deOtrosCpe(String nombreArchivo) { return nombreArchivo != null && DE_OTROS_CPE.matcher(nombreArchivo).matches(); }
}
