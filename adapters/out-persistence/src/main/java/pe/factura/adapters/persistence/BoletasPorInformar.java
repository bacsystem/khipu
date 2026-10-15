package pe.factura.adapters.persistence;

import pe.factura.domain.documento.PlazoEnvio;

import java.sql.Date;
import java.time.LocalDate;

/**
 * La regla de {@code Comprobante.porInformar} (#356) en SQL: lo de boleta que sigue sin informarse a SUNAT y ya pide atención. Es lo que va en el resumen diario ({@code Nota.vaEnResumenDiario}):
 * la boleta y la nota que la modifica, salvo la NC 10 y la ND 03, que no tienen resumen y, vencidas, pasan a FUERA_DE_PLAZO. Pide atención desde el 5.º día
 * ({@link PlazoEnvio#DIAS_ENVIO_INDIVIDUAL_BOLETA}); cada condición lleva un {@code ?}, que es la última fecha de emisión que ya cumple eso y se arma con {@link #corte}.
 * <p>
 * El alias del documento es {@code d}; el del comprobante lo pone quien llama (con {@code LEFT JOIN}: solo la nota lo necesita) porque en algunas consultas {@code c} ya es la cuenta.
 */
final class BoletasPorInformar {
    private BoletasPorInformar() {}

    private static String deBoleta(String c) {
        return "(d.tipo = '03' OR (" + c + ".nota_tipo_afectado = '03' AND NOT ((d.tipo = '07' AND " + c + ".nota_motivo = '10') OR (d.tipo = '08' AND " + c + ".nota_motivo = '03'))))";
    }

    /** Lo que está por enviar —nunca enviado (FIRMADO) o con el envío fallando (ERROR_ENVIO)— y ya pide atención. Un parámetro: {@link #corte}. */
    static String sinInformar(String c) { return "(d.estado IN ('FIRMADO', 'ERROR_ENVIO') AND d.fecha_emision <= ? AND " + deBoleta(c) + ")"; }

    /** Solo lo que nunca se envió: el envío fallando ya es un error de envío y se cuenta como tal. Un parámetro: {@link #corte}. */
    static String sinEnviar(String c) { return "(d.estado = 'FIRMADO' AND d.fecha_emision <= ? AND " + deBoleta(c) + ")"; }

    /** La última fecha de emisión que ya pide atención hoy: la de hace 5 días. */
    static Date corte(LocalDate hoy) { return Date.valueOf(hoy.minusDays(PlazoEnvio.DIAS_ENVIO_INDIVIDUAL_BOLETA)); }

    /** La última fecha de emisión a la que hoy le toca el aviso urgente (el último día del resumen diario, o pasado): la de hace 7 días. */
    static Date corteUrgente(LocalDate hoy) { return Date.valueOf(hoy.minusDays(PlazoEnvio.DIAS_RESUMEN_DIARIO)); }
}
