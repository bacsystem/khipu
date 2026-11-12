package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.PurgarTokensDeAccesoUseCase;
import pe.factura.application.port.out.PurgaDeRecuperaciones;
import pe.factura.application.port.out.PurgaDeVerificaciones;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class PurgarTokensDeAccesoService implements PurgarTokensDeAccesoUseCase {
    /** Filas por borrado: cada lote es su propia transacción y suelta sus bloqueos al terminar. */
    static final int LOTE = 1000;
    /** Tope por tabla y por pasada: un atraso grande se come en varias horas sin tener ocupado un hilo del planificador. */
    static final int MAX_LOTES = 50;

    private final PurgaDeRecuperaciones recuperaciones;
    private final PurgaDeVerificaciones verificaciones;
    private final Clock clock;
    private final Duration retencion;

    /**
     * Sin transacción alrededor de los bucles, a propósito: una sola transacción de hasta 50 000 filas sostendría los bloqueos todo ese tiempo.
     * Las dos tablas usan el mismo corte.
     */
    @Override public int purgar() {
        Instant corte = Instant.now(clock).minus(retencion);
        return purgarPorLotes(recuperaciones::purgarRecuperacionesVencidas, corte)
                + purgarPorLotes(verificaciones::purgarVerificacionesVencidas, corte);
    }

    private static int purgarPorLotes(PurgaPorLote purga, Instant corte) {
        int total = 0;
        for (int i = 0; i < MAX_LOTES; i++) {
            int borradas = purga.purgar(corte, LOTE);
            total += borradas;
            if (borradas < LOTE) break;
        }
        return total;
    }

    @FunctionalInterface
    private interface PurgaPorLote {
        int purgar(Instant vencidasAntesDe, int lote);
    }
}
