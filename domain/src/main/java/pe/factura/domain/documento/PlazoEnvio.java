package pe.factura.domain.documento;

import java.time.LocalDate;
import java.util.List;

/**
 * Plazo para que SUNAT reciba el comprobante (RS 193-2020): hasta el 3.er día calendario contado desde el día siguiente
 * a la emisión. Pasado ese día SUNAT rechaza ("Presentación fuera de fecha": 2108 en facturas y notas) y el emisor debe emitir
 * de nuevo. Los días son calendario: fines de semana y feriados cuentan.
 * <p>
 * Las boletas (#20) se envían una por una con sendBill hasta el 5.º día (RS 097-2012 arts. 7.3, 12 y 21, texto de la RS 114-2019; #290). Después
 * SUNAT ya no las recibe solas (1079: «Solo puede enviar el comprobante en un resumen diario») pero sí en un resumen diario, hasta el séptimo día
 * (guía del resumen diario): su plazo final es ese.
 * <p>
 * Lo que decide es si el documento es «de boleta», no su tipo (#293): una nota B sigue a la boleta que modifica (NotaCredito2_0 / NotaDebito2_0 filas 18–19:
 * 1079 pasado el 5.º día), y una nota F a la factura (2108). La NC 10 y la ND 03 de boleta no se informan en el resumen diario (guía, p. 19): pasado el envío
 * individual no les queda canal, así que su plazo final es el 5.º día.
 * <p>
 * La liquidación de compra (#327) va sola hasta el día calendario siguiente a su emisión: un día (RS 000003-2023; LiquidacionCompra2_0 fila 4, 2108 con el «límite del
 * listado (1 día)» para la serie que no empieza con número). No tiene resumen diario, así que vencida queda sin canal, como la factura.
 * <p>
 * El comprobante de retención y el de percepción (#40) van solos (sendBill al servicio de otros CPE) dentro del «plazo máximo vigente» (Retenciones1_0 y Percepciones1_0 fila 5,
 * regla 2600, que desde 2024-11-01 se cuenta «según normativa vigente para cada tipo de comprobante»). La hoja no publica el número. La norma (art. 41 de la RS 097-2012/SUNAT,
 * incorporado por la RS 274-2015/SUNAT) da «hasta por un plazo máximo de siete (7) días calendario desde el día siguiente de la fecha de emisión del CRE o CPE»: se cuenta como
 * los demás plazos de esta clase, del día siguiente a la emisión, y el 7.º día es el último ({@link #fechaLimite} = emisión + 7). Fuentes: perucontable.com y bizlinks.com.pe
 * (secundarias); el PDF de SUNAT de la RS 274-2015 es un escaneo que no se pudo leer aquí. e-beta no aplica la 2600 (2026-10-20 aceptó retenciones emitidas hace 2 a 40 días). khipu
 * da el número por consumido aunque SUNAT rechace (para SUNAT no lo está): un plazo local más largo que el real dejaría huecos en la serie, uno más corto no daña; por eso el más corto.
 * <p>
 * Pasado el 7.º día lo que va en el resumen diario no se queda sin canal (#331): SUNAT no rechaza el alta tardía (la norma prevé que la observe con la 4443; e-beta no lo hizo). El plazo sigue siendo
 * el 7.º día ({@link #dias}): pasarlo es informar fuera de plazo, no imposible. La anulación en el resumen sí tiene tope (2957, {@link ComunicacionBaja}).
 *
 * @param documento cómo se nombra en el mensaje de rechazo
 * @param diasEnvioIndividual hasta qué día va sola (sendBill)
 * @param dias el plazo final, contando el resumen diario si lo admite
 * @param deBoleta si SUNAT lo trata como boleta (1079) o como factura (2108)
 * @param reglaFueraDePlazo el código con que SUNAT rechaza lo que no es de boleta pasado el plazo: 2108 la factura, sus notas y la liquidación de compra; 2600 la retención y la percepción
 */
public record PlazoEnvio(String documento, int diasEnvioIndividual, int dias, boolean deBoleta, String reglaFueraDePlazo) {

    /** El envío individual (sendBill) de facturas y notas. */
    public static final int DIAS_ENVIO_INDIVIDUAL = 3;
    /** El envío individual (sendBill) de una boleta o de una nota que la modifica. */
    public static final int DIAS_ENVIO_INDIVIDUAL_BOLETA = 5;
    /** El envío individual (sendBill) de una liquidación de compra: hasta el día siguiente a la emisión (#327). */
    public static final int DIAS_ENVIO_LIQUIDACION_COMPRA = 1;
    /** El envío (sendBill) de un comprobante de retención o de percepción (#40): 7 días calendario desde el día siguiente a la emisión (RS 274-2015; ver la nota de la clase). */
    public static final int DIAS_ENVIO_AGENTE = 7;
    /** Hasta cuándo una boleta, o una nota que la modifica, se puede informar en un resumen diario. */
    public static final int DIAS_RESUMEN_DIARIO = 7;
    /**
     * Hasta cuántos días después de emitida se le sigue escribiendo al cliente por una boleta sin informar (#356). Pasado el 7.º día todavía se puede informar (fuera de plazo), y la cola y el
     * monitor del backoffice la siguen mostrando, pero un correo cada semana por una boleta que el cliente abandonó (una prueba, una venta que no siguió) sería spam sin fin y sin forma de
     * descartarla.
     */
    public static final int DIAS_AVISO_BOLETAS = 30;

    private static final String FUERA_DE_FECHA = "2108";
    private static final PlazoEnvio FACTURA = new PlazoEnvio("factura", DIAS_ENVIO_INDIVIDUAL, DIAS_ENVIO_INDIVIDUAL, false, FUERA_DE_FECHA);
    private static final PlazoEnvio NOTA_DE_FACTURA = new PlazoEnvio("nota", DIAS_ENVIO_INDIVIDUAL, DIAS_ENVIO_INDIVIDUAL, false, FUERA_DE_FECHA);
    private static final PlazoEnvio BOLETA = new PlazoEnvio("boleta", DIAS_ENVIO_INDIVIDUAL_BOLETA, DIAS_RESUMEN_DIARIO, true, "1079");
    private static final PlazoEnvio NOTA_DE_BOLETA = new PlazoEnvio("nota", DIAS_ENVIO_INDIVIDUAL_BOLETA, DIAS_RESUMEN_DIARIO, true, "1079");
    private static final PlazoEnvio NOTA_DE_BOLETA_FUERA_DEL_RESUMEN = new PlazoEnvio("nota", DIAS_ENVIO_INDIVIDUAL_BOLETA, DIAS_ENVIO_INDIVIDUAL_BOLETA, true, "1079");
    private static final PlazoEnvio LIQUIDACION_COMPRA = new PlazoEnvio("liquidación de compra", DIAS_ENVIO_LIQUIDACION_COMPRA, DIAS_ENVIO_LIQUIDACION_COMPRA, false, FUERA_DE_FECHA);
    private static final PlazoEnvio RETENCION = new PlazoEnvio("comprobante de retención", DIAS_ENVIO_AGENTE, DIAS_ENVIO_AGENTE, false, "2600");
    private static final PlazoEnvio PERCEPCION = new PlazoEnvio("comprobante de percepción", DIAS_ENVIO_AGENTE, DIAS_ENVIO_AGENTE, false, "2600");
    private static final List<PlazoEnvio> TODOS = List.of(FACTURA, NOTA_DE_FACTURA, BOLETA, NOTA_DE_BOLETA, NOTA_DE_BOLETA_FUERA_DEL_RESUMEN, LIQUIDACION_COMPRA, RETENCION, PERCEPCION);

    /** El plazo de un comprobante de este tipo; {@code nota} es la del comprobante, nula si no es una nota. */
    public static PlazoEnvio de(TipoDocumento tipo, Nota nota) {
        if (tipo == TipoDocumento.BOLETA) return BOLETA;
        if (tipo == TipoDocumento.LIQUIDACION_COMPRA) return LIQUIDACION_COMPRA;
        if (tipo == TipoDocumento.RETENCION) return RETENCION;
        if (tipo == TipoDocumento.PERCEPCION) return PERCEPCION;
        if (nota == null) return FACTURA;
        if (!nota.deBoleta()) return NOTA_DE_FACTURA;
        return nota.vaEnResumenDiario(tipo) ? NOTA_DE_BOLETA : NOTA_DE_BOLETA_FUERA_DEL_RESUMEN;
    }

    /** El más corto entre todos los plazos: para un corte grueso (p. ej. en SQL) que no dependa de cuál sea hoy el más restrictivo. */
    public static int diasMinimo() { return TODOS.stream().mapToInt(PlazoEnvio::dias).min().orElseThrow(); }

    /** Último día (inclusive) en que SUNAT acepta el envío (para lo de boleta, en un resumen diario si lo admite). */
    public LocalDate fechaLimite(LocalDate fechaEmision) { return fechaEmision.plusDays(dias); }

    /** Pasó el plazo legal ({@link #fechaLimite}). No dice si todavía hay canal: lo de boleta que va en el resumen se sigue informando ({@link #sinCanal}). */
    public boolean vencido(LocalDate fechaEmision, LocalDate hoy) { return hoy.isAfter(fechaLimite(fechaEmision)); }

    /** Si va en el resumen diario: lo de boleta salvo la NC 10 y la ND 03, cuyo plazo final es el del envío individual. */
    private boolean vaEnResumen() { return deBoleta && dias > diasEnvioIndividual; }

    /**
     * Venció y ya no le queda ningún canal: lo que pasa a FUERA_DE_PLAZO. La factura y sus notas (2108) y la NC 10 / ND 03 de boleta (1079). Lo que va en el
     * resumen diario nunca (#331): SUNAT no rechaza el alta tardía; la observa con la 4443 (Resumen Diario 1_1 fila 14) o, como en e-beta, la acepta sin observaciones.
     */
    public boolean sinCanal(LocalDate fechaEmision, LocalDate hoy) { return vencido(fechaEmision, hoy) && !vaEnResumen(); }

    /**
     * Se informa en el resumen diario pasado el 7.º día (#331): SUNAT puede observarlo con la 4443 «El comprobante fue informado fuera del plazo
     * establecido» (vigente desde 2024-11-11, Control de Cambios fila 501). Verificado en e-beta el 2026-10-13 (8 y 30 días de atraso): no la devolvió, lo aceptó
     * sin observaciones; la regla está en la hoja oficial y puede aplicarse en producción.
     */
    public boolean informaFueraDePlazo(LocalDate fechaEmision, LocalDate hoy) { return vaEnResumen() && vencido(fechaEmision, hoy); }

    /**
     * Ya no se puede enviar solo pero todavía se puede informar en un resumen diario (274-H1): desde el 6.º día de lo de boleta. Hasta el 7.º en plazo; después,
     * fuera de plazo ({@link #informaFueraDePlazo}, #331).
     */
    public boolean soloPorResumen(LocalDate fechaEmision, LocalDate hoy) {
        return vaEnResumen() && hoy.isAfter(fechaEmision.plusDays(diasEnvioIndividual));
    }

    /**
     * Si lo de boleta que sigue sin informarse ya pide atención (#356): desde el 5.º día, el último del envío individual. Hasta entonces el cliente tiene tiempo de sobra; desde
     * entonces solo le quedan el último día de {@code sendBill} y los dos del resumen diario. No cuenta lo que no va en el resumen (la factura, la NC 10, la ND 03): vencido pasa
     * a FUERA_DE_PLAZO, que ya pide atención por sí mismo.
     */
    public boolean porInformar(LocalDate fechaEmision, LocalDate hoy) {
        return vaEnResumen() && !hoy.isBefore(fechaEmision.plusDays(diasEnvioIndividual));
    }

    /**
     * Si al plazo del resumen diario le queda hoy el último día o ya lo pasó (#356): el 7.º día en adelante. Es el segundo aviso, el urgente: pasado, se informa fuera de plazo
     * y SUNAT puede observarlo con la 4443 ({@link #informaFueraDePlazo}).
     */
    public boolean porVencerElPlazo(LocalDate fechaEmision, LocalDate hoy) {
        return vaEnResumen() && !hoy.isBefore(fechaLimite(fechaEmision));
    }

    /**
     * Si el outbox debe seguir reintentando aunque agote sus intentos (#356): lo de boleta mientras siga en plazo del resumen diario. Con el backoff, 20 intentos son unas 74 horas:
     * una boleta que SUNAT no recibe el día de la emisión se rendía al 3.er día, con cuatro por delante en el resumen. Pasado el plazo ya puede agotarse; sigue visible y se
     * puede enviar a mano.
     */
    public boolean noSeAbandonaTodavia(LocalDate fechaEmision, LocalDate hoy) {
        return vaEnResumen() && !vencido(fechaEmision, hoy);
    }

    /**
     * Qué pasa pasado el plazo final, con el código de SUNAT. No es lo mismo en los dos (274-H1): una factura queda fuera de fecha (2108) y hay que emitir
     * otra; una boleta, pasados también los 7 días del resumen diario, ya no se recibe a tiempo ni en él. Lo de boleta que va en el resumen solo lo usa al
     * emitir (no se emite con una fecha ya vencida); lo ya firmado se informa igual, fuera de plazo (#331).
     */
    public String motivoDeRechazo(LocalDate fechaEmision) {
        LocalDate limite = fechaLimite(fechaEmision);
        if (!deBoleta) return reglaFueraDePlazo + (FUERA_DE_FECHA.equals(reglaFueraDePlazo) ? " - Presentación fuera de fecha" : " - El comprobante fue enviado fuera del plazo permitido")
                + ": el plazo de envío venció el " + limite + " (" + dias + (dias == 1 ? " día calendario)" : " días calendario)");
        if (dias == diasEnvioIndividual)
            return "1079 - El envío individual de esta " + documento + " venció el " + limite + " (" + dias + " días calendario) y una nota de crédito 10 o de débito 03"
                    + " no se informa en el resumen diario: ya no se puede presentar";
        return "1079 - Pasado el envío individual, SUNAT solo recibe esta " + documento + " en un resumen diario, y el plazo para informarla en él venció el " + limite
                + " (" + dias + " días calendario)";
    }
}
