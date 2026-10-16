package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.application.port.out.PadronRucRepository.ResultadoCarga;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #361: consultar un RUC en el padrón, con su cupo por empresa. */
class ConsultarPadronRucServiceTest {
    static final String RUC = "20100066603";
    final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-15T15:00:00Z"));
    final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return ahora.get(); }
    };
    final PadronEnMemoria padron = new PadronEnMemoria();
    final Fakes.Intentos cupo = new Fakes.Intentos();
    final ConsultarPadronRucService servicio = new ConsultarPadronRucService(padron, cupo, clock);
    final UUID empresa = UUID.randomUUID();

    @Test void devuelveElContribuyenteYDeQueCargaViene() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");
        padron.contribuyentes.add(new Contribuyente(RUC, "TIENDAS DEL SUR S.A.C.", "ACTIVO", "HABIDO", "150101", "AV. PERU NRO. 1"));

        var consulta = servicio.consultar(empresa, RUC);

        assertThat(consulta.contribuyente().razonSocial()).isEqualTo("TIENDAS DEL SUR S.A.C.");
        assertThat(consulta.actualizadoAl()).isEqualTo(Instant.parse("2026-10-15T09:40:00Z"));
    }

    @Test void unRucQueNoEstaEnElPadronEsNoEncontrado() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");

        assertThatThrownBy(() -> servicio.consultar(empresa, RUC)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void sinPadronCargadoNoAfirmaQueElRucNoExiste() {
        assertThatThrownBy(() -> servicio.consultar(empresa, RUC)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PADRON_NO_CARGADO");
    }

    @Test void unRucMalFormadoOConDigitoErradoSeRechazaSinTocarElPadronNiGastarCupo() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");

        for (String malo : new String[]{"2010006660", "20100066604", "abc", "", null}) {
            assertThatThrownBy(() -> servicio.consultar(empresa, malo)).as("«%s»", malo).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        }
        assertThat(padron.busquedas).isZero();
        assertThat(cupo.filas).isEmpty();
    }

    @Test void despuesDeSesentaConsultasPorMinutoElSesentaYunoSeFrenaSinBuscar() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");
        padron.contribuyentes.add(new Contribuyente(RUC, "TIENDAS", "ACTIVO", "HABIDO", null, null));
        for (int i = 0; i < 60; i++) servicio.consultar(empresa, RUC);
        int busquedas = padron.busquedas;

        assertThatThrownBy(() -> servicio.consultar(empresa, RUC)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DEMASIADAS_CONSULTAS");
        assertThat(padron.busquedas).as("la consulta frenada no toca el padrón").isEqualTo(busquedas);

        ahora.set(ahora.get().plus(Duration.ofMinutes(1)));
        assertThat(servicio.consultar(empresa, RUC).contribuyente().ruc()).as("pasado el minuto, vuelve").isEqualTo(RUC);
    }

    @Test void elCupoDelDiaLimitaAunqueCadaMinutoSeaPoco() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");
        padron.contribuyentes.add(new Contribuyente(RUC, "TIENDAS", "ACTIVO", "HABIDO", null, null));
        for (int i = 0; i < 5000; i++) {
            servicio.consultar(empresa, RUC);
            ahora.set(ahora.get().plusSeconds(10));
        }

        assertThatThrownBy(() -> servicio.consultar(empresa, RUC)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DEMASIADAS_CONSULTAS");
    }

    @Test void elCupoEsDeCadaEmpresa() {
        padron.cargada = Instant.parse("2026-10-15T09:40:00Z");
        padron.contribuyentes.add(new Contribuyente(RUC, "TIENDAS", "ACTIVO", "HABIDO", null, null));
        for (int i = 0; i < 60; i++) servicio.consultar(empresa, RUC);

        assertThat(servicio.consultar(UUID.randomUUID(), RUC)).as("otra empresa no se entera").isNotNull();
    }

    static final class PadronEnMemoria implements PadronRucRepository {
        final List<Contribuyente> contribuyentes = new ArrayList<>();
        Instant cargada;
        int busquedas;

        public Optional<Contribuyente> buscar(String ruc) {
            busquedas++;
            return contribuyentes.stream().filter(c -> c.ruc().equals(ruc)).findFirst();
        }
        public Optional<CargaRegistrada> ultimaCargaExitosa() {
            return cargada == null ? Optional.empty() : Optional.of(new CargaRegistrada(cargada.minusSeconds(60), cargada, true, "x", contribuyentes.size(), 0, 0, 0, 0, null));
        }
        public ResultadoCarga reemplazar(Stream<Contribuyente> filas, long minimoDeFilas) { throw new UnsupportedOperationException(); }
        public void registrar(CargaRegistrada carga) { throw new UnsupportedOperationException(); }
    }
}
