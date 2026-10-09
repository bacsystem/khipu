package pe.factura.domain.documento;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Plazo para que SUNAT reciba el comprobante (RS 193-2020): hasta el 3.er día calendario contado desde el día siguiente
 * a la emisión. Pasado ese día SUNAT rechaza ("Presentación fuera de fecha": 2108 en facturas y notas, 1079 en boletas) y el
 * emisor debe emitir de nuevo. Los días son calendario: fines de semana y feriados cuentan.
 * <p>
 * Las boletas (#20) se envían una por una con sendBill, como las facturas; las reglas de validación remiten al «plazo máximo vigente» (parámetro 004).
 */
public final class PlazoEnvio {
    private PlazoEnvio() {}

    public static int dias(TipoDocumento tipo) { return 3; }

    /** Código con que SUNAT rechaza un envío fuera de plazo: 1079 en Boleta2_0, 2108 en Factura2_0 y en las notas. */
    public static String reglaDeRechazo(TipoDocumento tipo) { return tipo == TipoDocumento.BOLETA ? "1079" : "2108"; }

    /**
     * Qué pasa pasado el plazo, con el código de SUNAT. No es lo mismo en los dos (274-H1): una factura queda fuera de fecha (2108) y hay que emitir otra;
     * una boleta no, SUNAT la sigue recibiendo, pero solo en un resumen diario (1079: «Solo puede enviar el comprobante en un resumen diario»).
     */
    public static String motivoDeRechazo(TipoDocumento tipo, LocalDate fechaEmision) {
        LocalDate limite = fechaLimite(tipo, fechaEmision);
        return tipo == TipoDocumento.BOLETA
                ? "1079 - Pasado el envío individual (venció el " + limite + "), SUNAT solo recibe esta boleta en un resumen diario"
                : "2108 - Presentación fuera de fecha: el plazo de envío venció el " + limite + " (" + dias(tipo) + " días calendario)";
    }

    /** El más corto entre todos los tipos: para un corte grueso (p. ej. en SQL) que no dependa de cuál sea hoy el más restrictivo. */
    public static int diasMinimo() { return Arrays.stream(TipoDocumento.values()).mapToInt(PlazoEnvio::dias).min().orElseThrow(); }

    /** Último día (inclusive) en que SUNAT acepta el envío. */
    public static LocalDate fechaLimite(TipoDocumento tipo, LocalDate fechaEmision) { return fechaEmision.plusDays(dias(tipo)); }

    public static boolean vencido(TipoDocumento tipo, LocalDate fechaEmision, LocalDate hoy) { return hoy.isAfter(fechaLimite(tipo, fechaEmision)); }
}
