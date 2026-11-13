package pe.factura.application.port.out;

import java.time.Instant;

/** Borrado de los enlaces de verificación de correo vencidos (#634). Puerto aparte de {@link VerificacionCorreoRepository}: lo usa solo la limpieza. */
public interface PurgaDeVerificaciones {
    /**
     * Borra hasta {@code lote} tokens de verificación cuyo vencimiento es anterior a {@code vencidasAntesDe}, estén usados o no. Nunca toca uno sin vencer:
     * el «ya se usó» de un enlace vigente y el tope de reenvíos dependen de ellos. No espera a las filas que otra transacción tiene tomadas.
     * Devuelve cuántos borró.
     */
    int purgarVerificacionesVencidas(Instant vencidasAntesDe, int lote);
}
