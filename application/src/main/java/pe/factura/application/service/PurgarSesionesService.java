package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.PurgarSesionesUseCase;
import pe.factura.application.port.out.PurgaDeSesiones;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class PurgarSesionesService implements PurgarSesionesUseCase {
    /** Filas por borrado: cada lote es su propia transacción y suelta sus bloqueos al terminar. */
    static final int LOTE = 1000;
    /** Tope por pasada: un atraso grande se come en varias horas sin tener ocupado un hilo del planificador. */
    static final int MAX_LOTES = 50;

    private final PurgaDeSesiones purga;
    private final Clock clock;
    private final Duration retencion;

    /**
     * Sin transacción alrededor del bucle, a propósito: una sola transacción de hasta 50 000 filas sostendría los bloqueos todo ese tiempo.
     */
    @Override public int purgar() {
        Instant corte = Instant.now(clock).minus(retencion);
        int total = 0;
        for (int i = 0; i < MAX_LOTES; i++) {
            int borradas = purga.purgarVencidas(corte, LOTE);
            total += borradas;
            if (borradas < LOTE) break;
        }
        return total;
    }
}
