package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeEmpresa;
import pe.factura.application.port.out.ConsumoRepository;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** La consulta de consumo (#192): el mes en curso es el de Lima, la cuenta suma sus empresas y lo que no existe es 404. */
class ConsultarConsumoServiceTest {
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    UUID cuentaId = UUID.randomUUID();
    UUID e1 = UUID.randomUUID();
    UUID e2 = UUID.randomUUID();

    Fakes.Tenants tenants = new Fakes.Tenants();
    {
        tenants.guardar(new Tenant(e1, "20100066603", "UNO SAC", Entorno.BETA, null, null));
        tenants.guardar(new Tenant(e2, "20100066611", "DOS SAC", Entorno.BETA, null, null));
    }

    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { throw new AssertionError("consultar no escribe"); }
        public Optional<Cuenta> buscar(UUID id) { return cuentaId.equals(id) ? Optional.of(new Cuenta(cuentaId, "Mi negocio", "ana@negocio.pe")) : Optional.empty(); }
        public Optional<Cuenta> buscarPorEmail(String email) { throw new AssertionError("no se busca por correo"); }
    };

    /** Lo que el repositorio real devolvería, con las llamadas que recibió. */
    static class Consumos implements ConsumoRepository {
        final List<String> llamadas = new ArrayList<>();
        public long documentosDeEmpresa(UUID t, YearMonth mes) { llamadas.add("empresa " + mes); return 7; }
        public List<ConsumoDeEmpresa> documentosPorEmpresaDeCuenta(UUID c, YearMonth mes) {
            llamadas.add("cuenta " + mes);
            return List.of(new ConsumoDeEmpresa(UUID.fromString("00000000-0000-4000-9000-000000000001"), "20100066603", "UNO SAC", 5),
                    new ConsumoDeEmpresa(UUID.fromString("00000000-0000-4000-9000-000000000002"), "20100066611", "DOS SAC", 0),
                    new ConsumoDeEmpresa(UUID.fromString("00000000-0000-4000-9000-000000000003"), "20100066620", "TRES SAC", 12));
        }
    }

    Consumos consumos = new Consumos();

    ConsultarConsumoService servicio(Clock reloj) { return new ConsultarConsumoService(consumos, cuentas, tenants, reloj); }

    ConsultarConsumoService service = servicio(Fakes.CLOCK);

    static Clock en(String instante) { return Clock.fixed(Instant.parse(instante), ZoneId.of("UTC")); }

    // --- el mes -----------------------------------------------------------------------------------------------------------------------------

    @Test void elMesEnCursoEsElDeLimaNoElDeUtc() {
        // 03:00 UTC del 1 de noviembre todavía es el 31 de octubre en Lima.
        assertThat(servicio(en("2026-11-01T03:00:00Z")).mesActual()).isEqualTo(OCTUBRE);
        assertThat(servicio(en("2026-11-01T05:00:00Z")).mesActual()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(servicio(en("2026-11-01T04:59:59Z")).mesActual()).isEqualTo(OCTUBRE);
    }

    @Test void sinMesSeConsultaElMesEnCurso() {
        service.deEmpresa(e1, null);
        service.deCuenta(cuentaId, null);

        // Fakes.CLOCK es el 13 de septiembre de 2026.
        assertThat(consumos.llamadas).containsExactly("empresa 2026-09", "cuenta 2026-09");
    }

    @Test void conMesSeConsultaEseMes() {
        service.deEmpresa(e1, OCTUBRE);
        service.deCuenta(cuentaId, YearMonth.of(2025, 12));

        assertThat(consumos.llamadas).containsExactly("empresa 2026-10", "cuenta 2025-12");
    }

    // --- por empresa ------------------------------------------------------------------------------------------------------------------------

    @Test void elConsumoDeUnaEmpresaDiceSuRucSuRazonSocialElMesYLosDocumentos() {
        ConsumoDeEmpresa c = service.deEmpresa(e1, OCTUBRE);

        assertThat(c).isEqualTo(new ConsumoDeEmpresa(e1, "20100066603", "UNO SAC", OCTUBRE, 7));
    }

    @Test void unaEmpresaQueNoExisteEsNoEncontrada() {
        assertThat(catchThrowableOfType(DomainException.class, () -> service.deEmpresa(UUID.randomUUID(), OCTUBRE)).codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(catchThrowableOfType(DomainException.class, () -> service.deEmpresa(null, OCTUBRE)).codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(consumos.llamadas).isEmpty();
    }

    // --- por cuenta -------------------------------------------------------------------------------------------------------------------------

    @Test void elConsumoDeUnaCuentaSumaSusEmpresasYLasDetalla() {
        ConsumoDeCuenta c = service.deCuenta(cuentaId, OCTUBRE);

        assertThat(c.cuentaId()).isEqualTo(cuentaId);
        assertThat(c.mes()).isEqualTo(OCTUBRE);
        assertThat(c.documentos()).isEqualTo(17);
        assertThat(c.empresas()).extracting(ConsumoDeEmpresa::documentos).containsExactly(5L, 0L, 12L);
        assertThat(c.empresas()).extracting(ConsumoDeEmpresa::razonSocial).containsExactly("UNO SAC", "DOS SAC", "TRES SAC");
        assertThat(c.empresas()).allSatisfy(e -> assertThat(e.mes()).isEqualTo(OCTUBRE));
    }

    @Test void unaCuentaSinEmpresasConsumeCero() {
        ConsultarConsumoService sinEmpresas = new ConsultarConsumoService(new ConsumoRepository() {
            public long documentosDeEmpresa(UUID t, YearMonth mes) { return 0; }
            public List<ConsumoDeEmpresa> documentosPorEmpresaDeCuenta(UUID c, YearMonth mes) { return List.of(); }
        }, cuentas, tenants, Fakes.CLOCK);

        ConsumoDeCuenta c = sinEmpresas.deCuenta(cuentaId, OCTUBRE);

        assertThat(c.documentos()).isZero();
        assertThat(c.empresas()).isEmpty();
    }

    @Test void unaCuentaQueNoExisteEsNoEncontrada() {
        assertThat(catchThrowableOfType(DomainException.class, () -> service.deCuenta(UUID.randomUUID(), OCTUBRE)).codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(catchThrowableOfType(DomainException.class, () -> service.deCuenta(null, OCTUBRE)).codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(consumos.llamadas).isEmpty();
    }
}
