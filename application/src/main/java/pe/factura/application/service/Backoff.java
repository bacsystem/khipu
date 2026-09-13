package pe.factura.application.service;

import java.time.Duration;
import java.time.Instant;

public final class Backoff {
    private static final Duration TOPE = Duration.ofHours(6);
    private Backoff() {}
    public static Instant siguiente(int intentos, Instant ahora) {
        int exp = Math.min(intentos, 20);
        Duration d = Duration.ofMinutes(1L << exp);
        return ahora.plus(d.compareTo(TOPE) > 0 ? TOPE : d);
    }
}
