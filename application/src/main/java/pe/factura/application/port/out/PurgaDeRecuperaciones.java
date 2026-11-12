package pe.factura.application.port.out;

import java.time.Instant;

/**
 * Borrado de los enlaces de recuperación de contraseña e invitación vencidos (#634). Puerto aparte de {@link SesionRepository} y de {@link PurgaDeSesiones}:
 * el método tiene otro nombre a propósito, porque con la firma de {@code purgarVencidas} el adaptador que implementa los dos puertos los uniría en un solo
 * método y esta purga borraría filas de {@code sesion}.
 */
public interface PurgaDeRecuperaciones {
    /**
     * Borra hasta {@code lote} tokens de recuperación cuyo vencimiento es anterior a {@code vencidasAntesDe}, estén usados o no. Nunca toca uno sin vencer.
     * No espera a las filas que otra transacción tiene tomadas. Devuelve cuántos borró.
     */
    int purgarRecuperacionesVencidas(Instant vencidasAntesDe, int lote);
}
