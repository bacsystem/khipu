package pe.factura.domain.documento;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Plazo para que SUNAT reciba el comprobante (RS 193-2020): hasta el 3.er día calendario contado desde el día siguiente
 * a la emisión, para facturas y sus notas. Pasado ese día SUNAT rechaza con 2108 ("Presentación fuera de fecha") y el
 * emisor debe emitir de nuevo. Los días son calendario: fines de semana y feriados cuentan.
 * <p>
 * Las boletas se informan en el resumen diario (#20) con su propio plazo; hasta que exista, se les aplica el mismo.
 */
public final class PlazoEnvio {
    private PlazoEnvio() {}

    public static int dias(TipoDocumento tipo) { return 3; }

    /** El más corto entre todos los tipos: para un corte grueso (p. ej. en SQL) que no dependa de cuál sea hoy el más restrictivo. */
    public static int diasMinimo() { return Arrays.stream(TipoDocumento.values()).mapToInt(PlazoEnvio::dias).min().orElseThrow(); }

    /** Último día (inclusive) en que SUNAT acepta el envío. */
    public static LocalDate fechaLimite(TipoDocumento tipo, LocalDate fechaEmision) { return fechaEmision.plusDays(dias(tipo)); }

    public static boolean vencido(TipoDocumento tipo, LocalDate fechaEmision, LocalDate hoy) { return hoy.isAfter(fechaLimite(tipo, fechaEmision)); }
}
