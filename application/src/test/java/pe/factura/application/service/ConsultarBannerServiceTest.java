package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.BannerRepository.Guardado;
import pe.factura.domain.plataforma.BannerDeMantenimiento;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** El aviso de mantenimiento que ve todo cliente (#199): solo mientras su vigencia incluye el instante actual. */
class ConsultarBannerServiceTest {
    static final Instant AHORA = Fakes.CLOCK.instant();

    ConfiguracionFake.Banner banners = new ConfiguracionFake.Banner();
    ConsultarBannerService service = new ConsultarBannerService(banners, Fakes.CLOCK);

    void publicado(Instant desde, Instant hasta) { banners.fila = new Guardado(new BannerDeMantenimiento("Mantenimiento esta noche", desde, hasta), AHORA.minus(Duration.ofDays(1))); }

    @Test void sinBannerNoHayNadaQueMostrar() {
        assertThat(service.vigente()).isEmpty();
    }

    @Test void unBannerVigenteSeMuestraConSuTextoYSuVigencia() {
        publicado(AHORA.minus(Duration.ofHours(1)), AHORA.plus(Duration.ofHours(1)));

        assertThat(service.vigente()).contains(new BannerDeMantenimiento("Mantenimiento esta noche", AHORA.minus(Duration.ofHours(1)), AHORA.plus(Duration.ofHours(1))));
    }

    @Test void unBannerProgramadoParaDespuesNoSeMuestraTodavia() {
        publicado(AHORA.plusSeconds(1), AHORA.plus(Duration.ofHours(1)));

        assertThat(service.vigente()).isEmpty();
    }

    @Test void unBannerVencidoDejaDeMostrarse() {
        publicado(AHORA.minus(Duration.ofHours(2)), AHORA.minus(Duration.ofSeconds(1)));

        assertThat(service.vigente()).isEmpty();
    }

    @Test void elBordeDelInicioSeMuestraYElBordeDelFinYaNo() {
        publicado(AHORA, AHORA.plus(Duration.ofHours(1)));
        assertThat(service.vigente()).isPresent();

        publicado(AHORA.minus(Duration.ofHours(1)), AHORA);
        assertThat(service.vigente()).isEmpty();
    }
}
