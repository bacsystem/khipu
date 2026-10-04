package pe.factura.application.port.in;

import pe.factura.application.port.out.SondeoDeSunat.Resultado;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * El monitor global de emisión del backoffice (#195): cómo va la emisión de **todas** las empresas en las últimas 24 horas, cómo está la cola de envíos a SUNAT y si SUNAT
 * contesta. Lectura pura. Lo que es «aceptado», «en camino» o «con error» lo define {@code EstadoDocumento}.
 */
public interface MonitorearEmisionUseCase {
    /** Cuántas horas muestra la serie. */
    int HORAS = 24;

    /** Pasado este tiempo con envíos vencidos sin que nadie los tome, se da la alerta de que el trabajo del outbox no corre. */
    Duration UMBRAL_DE_ALERTA = Duration.ofMinutes(5);

    /**
     * Los comprobantes creados en una hora (o en el día, en {@link Monitor#hoy()}), por lo que ocurrió con ellos. {@code aceptados}: SUNAT los aceptó, con o sin
     * observaciones. {@code rechazados}: SUNAT los rechazó. {@code conError}: no llegaron (error de envío o fuera de plazo). {@code enCamino}: firmados, enviados o a la
     * espera del resumen. {@code otros}: recibidos, inválidos o dados de baja.
     */
    record Franja(Instant desde, long aceptados, long rechazados, long conError, long enCamino, long otros) {
        public long total() { return aceptados + rechazados + conError + enCamino + otros; }

        /** Rechazados sobre los que SUNAT ya resolvió (aceptados + rechazados); nulo si todavía no resolvió ninguno. */
        public Double tasaDeRechazo() {
            long resueltos = aceptados + rechazados;
            return resueltos == 0 ? null : (double) rechazados / resueltos;
        }
    }

    /**
     * La cola del outbox. {@code vencidoHace}: cuánto lleva esperando el envío vencido que más espera; nulo si no hay vencidos. {@code alerta}: lleva más que
     * {@link #UMBRAL_DE_ALERTA}: el trabajo que vacía la cola probablemente no corre.
     */
    record Outbox(long pendientes, long vencidos, Instant masViejoDesde, Duration vencidoHace, boolean alerta) {}

    /** {@code horas}: las últimas {@link #HORAS}, de la más vieja a la actual, con las vacías en cero. {@code hoy}: el día de Lima hasta ahora. */
    record Monitor(Instant generadoEn, List<Franja> horas, Franja hoy, Outbox outbox, List<Resultado> sunat) {}

    Monitor monitorear();
}
