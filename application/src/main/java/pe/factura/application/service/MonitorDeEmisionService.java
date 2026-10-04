package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.MonitorearEmisionUseCase;
import pe.factura.application.port.out.MonitorDeEmisionRepository;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Cola;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Conteo;
import pe.factura.application.port.out.SondeoDeSunat;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.plan.CicloMensual;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * El monitor global de emisión (#195). Solo lectura. El repositorio trae lo crudo (comprobantes por hora y estado, la cola del outbox) y acá se decide qué estados son
 * «aceptados», «con error» o «en camino» con las reglas de {@link EstadoDocumento}, se rellenan las horas sin comprobantes y se da la alerta del outbox.
 */
@RequiredArgsConstructor
public class MonitorDeEmisionService implements MonitorearEmisionUseCase {
    private static final Duration UNA_HORA = Duration.ofHours(1);

    private final MonitorDeEmisionRepository monitor;
    private final SondeoDeSunat sondeo;
    private final Clock clock;

    @Override public Monitor monitorear() {
        Instant ahora = clock.instant();
        Instant horaActual = ahora.truncatedTo(ChronoUnit.HOURS);
        Instant desde = horaActual.minus(UNA_HORA.multipliedBy(HORAS - 1));
        Map<Instant, List<Conteo>> porHora = monitor.porHoraYEstado(desde).stream().collect(Collectors.groupingBy(Conteo::hora));

        List<Franja> horas = new ArrayList<>(HORAS);
        for (int i = 0; i < HORAS; i++) {
            Instant hora = desde.plus(UNA_HORA.multipliedBy(i));
            horas.add(franja(hora, porHora.getOrDefault(hora, List.of())));
        }
        // El día de Lima empieza en una frontera de hora (UTC-5 sin horario de verano) y nunca está a más de 24 horas, así que las horas de la serie lo cubren entero.
        Instant medianoche = ahora.atZone(CicloMensual.ZONA).toLocalDate().atStartOfDay(CicloMensual.ZONA).toInstant();
        List<Conteo> deHoy = porHora.entrySet().stream().filter(e -> !e.getKey().isBefore(medianoche) && !e.getKey().isAfter(horaActual)).flatMap(e -> e.getValue().stream()).toList();

        return new Monitor(ahora, List.copyOf(horas), franja(medianoche, deHoy), outbox(monitor.cola(ahora), ahora), sondeo.sondear());
    }

    private static Franja franja(Instant desde, List<Conteo> conteos) {
        return new Franja(desde, cuantos(conteos, EstadoDocumento::esFinalAceptado), cuantos(conteos, e -> e == EstadoDocumento.RECHAZADO),
                cuantos(conteos, e -> e.requiereAtencion() && e != EstadoDocumento.RECHAZADO), cuantos(conteos, EstadoDocumento::estaEnCamino),
                cuantos(conteos, e -> !e.esFinalAceptado() && !e.requiereAtencion() && !e.estaEnCamino()));
    }

    private static long cuantos(List<Conteo> conteos, Predicate<EstadoDocumento> cuenta) {
        return conteos.stream().filter(c -> cuenta.test(c.estado())).mapToLong(Conteo::cantidad).sum();
    }

    private static Outbox outbox(Cola cola, Instant ahora) {
        Duration vencidoHace = cola.vencidoDesde() == null ? null : max(Duration.between(cola.vencidoDesde(), ahora), Duration.ZERO);
        return new Outbox(cola.pendientes(), cola.vencidos(), cola.masViejoDesde(), vencidoHace, vencidoHace != null && vencidoHace.compareTo(UMBRAL_DE_ALERTA) > 0);
    }

    private static Duration max(Duration a, Duration b) { return a.compareTo(b) >= 0 ? a : b; }
}
