package pe.factura.domain.documento;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Plazo para que SUNAT reciba el comprobante (RS 193-2020): hasta el 3.er día calendario contado desde el día siguiente
 * a la emisión. Pasado ese día SUNAT rechaza ("Presentación fuera de fecha": 2108 en facturas y notas) y el emisor debe emitir
 * de nuevo. Los días son calendario: fines de semana y feriados cuentan.
 * <p>
 * Las boletas (#20) se envían una por una con sendBill hasta el 5.º día (RS 097-2012 arts. 7.3, 12 y 21, texto de la RS 114-2019; #290). Después
 * SUNAT ya no las recibe solas (1079: «Solo puede enviar el comprobante en un resumen diario») pero sí en un resumen diario, hasta el séptimo día
 * (guía del resumen diario): su plazo final es ese.
 */
public final class PlazoEnvio {
    private PlazoEnvio() {}

    /** El envío individual (sendBill) de facturas y notas. */
    public static final int DIAS_ENVIO_INDIVIDUAL = 3;
    /** El envío individual (sendBill) de una boleta. */
    public static final int DIAS_ENVIO_INDIVIDUAL_BOLETA = 5;
    /** Hasta cuándo una boleta se puede informar en un resumen diario. */
    public static final int DIAS_RESUMEN_DIARIO = 7;

    /** Hasta qué día el comprobante se puede enviar solo (sendBill): 3, o 5 para una boleta. */
    public static int diasEnvioIndividual(TipoDocumento tipo) { return tipo == TipoDocumento.BOLETA ? DIAS_ENVIO_INDIVIDUAL_BOLETA : DIAS_ENVIO_INDIVIDUAL; }

    /** El plazo final: 3 días, o 7 para una boleta, que pasado el envío individual todavía va en un resumen diario. */
    public static int dias(TipoDocumento tipo) { return tipo == TipoDocumento.BOLETA ? DIAS_RESUMEN_DIARIO : DIAS_ENVIO_INDIVIDUAL; }

    /** Una boleta que ya no se puede enviar sola pero todavía se puede informar en un resumen diario (274-H1): el 6.º y el 7.º día. */
    public static boolean soloPorResumen(TipoDocumento tipo, LocalDate fechaEmision, LocalDate hoy) {
        return tipo == TipoDocumento.BOLETA && hoy.isAfter(fechaEmision.plusDays(diasEnvioIndividual(tipo))) && !vencido(tipo, fechaEmision, hoy);
    }

    /** Código con que SUNAT rechaza un envío fuera de plazo: 1079 en Boleta2_0, 2108 en Factura2_0 y en las notas. */
    public static String reglaDeRechazo(TipoDocumento tipo) { return tipo == TipoDocumento.BOLETA ? "1079" : "2108"; }

    /**
     * Qué pasa pasado el plazo final, con el código de SUNAT. No es lo mismo en los dos (274-H1): una factura queda fuera de fecha (2108) y hay que emitir
     * otra; una boleta, pasados también los 7 días del resumen diario, ya no se recibe a tiempo ni en él.
     */
    public static String motivoDeRechazo(TipoDocumento tipo, LocalDate fechaEmision) {
        LocalDate limite = fechaLimite(tipo, fechaEmision);
        return tipo == TipoDocumento.BOLETA
                ? "1079 - Pasado el envío individual, SUNAT solo recibe esta boleta en un resumen diario, y el plazo para informarla en él venció el " + limite
                        + " (" + DIAS_RESUMEN_DIARIO + " días calendario)"
                : "2108 - Presentación fuera de fecha: el plazo de envío venció el " + limite + " (" + dias(tipo) + " días calendario)";
    }

    /** El más corto entre todos los tipos: para un corte grueso (p. ej. en SQL) que no dependa de cuál sea hoy el más restrictivo. */
    public static int diasMinimo() { return Arrays.stream(TipoDocumento.values()).mapToInt(PlazoEnvio::dias).min().orElseThrow(); }

    /** Último día (inclusive) en que SUNAT acepta el envío (para una boleta, en un resumen diario). */
    public static LocalDate fechaLimite(TipoDocumento tipo, LocalDate fechaEmision) { return fechaEmision.plusDays(dias(tipo)); }

    public static boolean vencido(TipoDocumento tipo, LocalDate fechaEmision, LocalDate hoy) { return hoy.isAfter(fechaLimite(tipo, fechaEmision)); }
}
