package pe.factura.domain.plan;

import java.time.Instant;
import java.time.ZoneId;

/**
 * El ciclo de un plan es el mes calendario en America/Lima (UTC-5, sin horario de verano): ahí se cuenta el consumo de cada mes y ahí entran los cambios de
 * límites, para que subir o bajar un tope a mitad de mes no regale documentos del ciclo en curso ni le corte a nadie.
 */
public final class CicloMensual {
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    private CicloMensual() {}

    /** El primer instante del mes que viene (la medianoche del día 1, hora de Lima). Un instante que ya es el inicio de un ciclo pertenece a ese ciclo. */
    public static Instant inicioDelSiguiente(Instant ahora) {
        return ahora.atZone(ZONA).toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZONA).toInstant();
    }
}
