package pe.factura.application.port.out;

import java.time.Instant;

/** Borrado de las sesiones de refresh que ya no sirven para nada (#611). Puerto aparte de {@link SesionRepository}: lo usa solo la limpieza. */
public interface PurgaDeSesiones {
    /**
     * Borra hasta {@code lote} sesiones cuyo vencimiento es anterior a {@code vencidasAntesDe}, salvo la más reciente de cada usuario: el backoffice
     * deduce el último acceso del usuario de ella. No espera a las filas que otra transacción tiene tomadas. Devuelve cuántas borró.
     */
    int purgarVencidas(Instant vencidasAntesDe, int lote);
}
