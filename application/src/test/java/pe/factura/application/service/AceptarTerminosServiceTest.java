package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AceptarTerminosServiceTest {
    Clock clock = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    Fakes.Aceptaciones aceptaciones = new Fakes.Aceptaciones();
    AceptarTerminosService service = new AceptarTerminosService(aceptaciones, Fakes.UOW, clock, "2026-10-20");
    UUID usuario = UUID.randomUUID();

    @Test void sinAceptacionEstaPendiente() {
        assertThat(service.versionVigente()).isEqualTo("2026-10-20");
        assertThat(service.pendiente(usuario)).isTrue();
    }

    @Test void aceptarGuardaLaVersionVigenteYLaFecha() {
        AceptacionDeTerminos a = service.aceptar(usuario);

        assertThat(a).isEqualTo(new AceptacionDeTerminos(usuario, "2026-10-20", clock.instant()));
        assertThat(aceptaciones.buscar(usuario, "2026-10-20")).contains(a);
        assertThat(service.pendiente(usuario)).isFalse();
    }

    @Test void aceptarOtraVezNoMueveLaFecha() {
        service.aceptar(usuario);
        AceptarTerminosService despues = new AceptarTerminosService(aceptaciones, Fakes.UOW, Clock.offset(clock, Duration.ofDays(3)), "2026-10-20");

        assertThat(despues.aceptar(usuario).aceptadaEn()).isEqualTo(clock.instant());
    }

    /** Lo aceptado sigue guardado: la versión nueva se suma a la anterior, y ahí queda la constancia de cada una. */
    @Test void unaVersionNuevaQuedaPendienteYSeSumaALaAnterior() {
        service.aceptar(usuario);
        AceptarTerminosService nueva = new AceptarTerminosService(aceptaciones, Fakes.UOW, Clock.offset(clock, Duration.ofDays(90)), "2027-01-15");

        assertThat(nueva.pendiente(usuario)).isTrue();
        nueva.aceptar(usuario);

        assertThat(nueva.pendiente(usuario)).isFalse();
        assertThat(aceptaciones.filas).hasSize(2);
        assertThat(aceptaciones.buscar(usuario, "2026-10-20")).get().extracting(AceptacionDeTerminos::aceptadaEn).isEqualTo(clock.instant());
    }

    /** La versión viene de la configuración del despliegue: mal escrita, la aplicación no arranca. */
    @Test void unaVersionInvalidaNoArranca() {
        assertThatThrownBy(() -> new AceptarTerminosService(aceptaciones, Fakes.UOW, clock, " "))
                .extracting("codigo").isEqualTo("VERSION_TERMINOS_INVALIDA");
    }
}
