package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase.Fila;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.application.port.out.ConsumoPorCuentaRepository;
import pe.factura.application.port.out.ConsumoPorCuentaRepository.Registro;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La tabla de consumo de todas las cuentas (#193): el porcentaje y la alerta salen de una sola definición, y el estado de pago de la regla única del dominio. */
class ConsultarConsumoDeCuentasServiceTest {
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    static final Instant AHORA = Fakes.CLOCK.instant();

    /** Guarda con qué lo consultaron y devuelve lo que se le diga. */
    static class Consumos implements ConsumoPorCuentaRepository {
        final List<Consulta> consultas = new ArrayList<>();
        final List<String> llamadas = new ArrayList<>();
        List<Registro> registros = List.of();
        public List<Registro> listar(Consulta c, int pagina, int porPagina) { consultas.add(c); llamadas.add("listar " + pagina + "/" + porPagina); return registros; }
        public long contar(Consulta c) { consultas.add(c); llamadas.add("contar"); return 42; }
        public List<Registro> todas(Consulta c) { consultas.add(c); llamadas.add("todas"); return registros; }
    }

    Consumos consumos = new Consumos();
    ConsultarConsumoDeCuentasService service = new ConsultarConsumoDeCuentasService(consumos, Fakes.CLOCK);

    static Registro registro(String nombre, long documentos, Integer limite, Instant vence, int gracia) {
        return new Registro(UUID.randomUUID(), nombre, nombre.toLowerCase() + "@negocio.pe", UUID.randomUUID(), "Emprende", documentos, limite, vence, gracia);
    }

    Fila una(Registro r) {
        consumos.registros = List.of(r);
        return service.listar(OCTUBRE, FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE, 1, 20).get(0);
    }

    // --- la consulta ------------------------------------------------------------------------------------------------------------------------

    @Test void pasaAlRepositorioElMesElFiltroElOrdenElAhoraYElUmbralDeAlerta() {
        service.listar(OCTUBRE, FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.DOCUMENTOS, 3, 25);

        assertThat(consumos.consultas.get(0)).isEqualTo(new ConsumoPorCuentaRepository.Consulta(OCTUBRE, AHORA, FiltroDeConsumo.CERCA_DEL_LIMITE, OrdenDeConsumo.DOCUMENTOS, 80));
        assertThat(consumos.llamadas).containsExactly("listar 3/25");
    }

    @Test void sinMesFiltroNiOrdenSonElMesEnCursoTodasYPorPorcentaje() {
        service.listar(null, null, null, 1, 20);

        assertThat(consumos.consultas.get(0)).isEqualTo(new ConsumoPorCuentaRepository.Consulta(YearMonth.of(2026, 9), AHORA, FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE, 80));
    }

    @Test void elMesEnCursoEsElDeLimaNoElDeUtc() {
        Clock casiNoviembre = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("UTC"));
        Clock yaNoviembre = Clock.fixed(Instant.parse("2026-11-01T05:00:00Z"), ZoneId.of("UTC"));

        assertThat(new ConsultarConsumoDeCuentasService(consumos, casiNoviembre).mesActual()).isEqualTo(OCTUBRE);
        assertThat(new ConsultarConsumoDeCuentasService(consumos, yaNoviembre).mesActual()).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test void contarYTodasPasanLoMismoYSinPaginar() {
        assertThat(service.contar(OCTUBRE, FiltroDeConsumo.PLAN_VENCIDO)).isEqualTo(42);
        service.todas(OCTUBRE, FiltroDeConsumo.PLAN_VENCIDO, OrdenDeConsumo.DOCUMENTOS);

        assertThat(consumos.llamadas).containsExactly("contar", "todas");
        assertThat(consumos.consultas.get(0).filtro()).isEqualTo(FiltroDeConsumo.PLAN_VENCIDO);
        assertThat(consumos.consultas.get(1).orden()).isEqualTo(OrdenDeConsumo.DOCUMENTOS);
    }

    // --- la fila ----------------------------------------------------------------------------------------------------------------------------

    @Test void laFilaDiceElPorcentajeDelTopeYSiYaEstaEnAlerta() {
        Fila f = una(registro("Ana", 240, 300, AHORA.plus(Duration.ofDays(10)), 0));

        assertThat(f.documentos()).isEqualTo(240);
        assertThat(f.limite()).isEqualTo(Limite.de(300));
        assertThat(f.porcentaje()).isEqualTo(80);
        assertThat(f.enAlerta()).isTrue();
        assertThat(f.nombre()).isEqualTo("Ana");
        assertThat(f.email()).isEqualTo("ana@negocio.pe");
        assertThat(f.planNombre()).isEqualTo("Emprende");
    }

    @Test void justoPorDebajoDelUmbralNoAlerta() {
        Fila f = una(registro("Ana", 239, 300, null, 0));

        assertThat(f.porcentaje()).isEqualTo(79);
        assertThat(f.enAlerta()).isFalse();
    }

    @Test void unPlanSinTopeNoTienePorcentajeNiAlerta() {
        Fila f = una(registro("Ana", 100_000, null, null, 0));

        assertThat(f.limite().ilimitado()).isTrue();
        assertThat(f.porcentaje()).isNull();
        assertThat(f.enAlerta()).isFalse();
    }

    @Test void pasarseDelTopeEsMasDeCien() {
        Fila f = una(registro("Ana", 450, 300, null, 0));

        assertThat(f.porcentaje()).isEqualTo(150);
        assertThat(f.enAlerta()).isTrue();
    }

    // --- el estado de pago ------------------------------------------------------------------------------------------------------------------

    @Test void unPlanSinVencimientoEstaAlDiaYNoDiceHastaCuando() {
        Fila f = una(registro("Ana", 0, 30, null, 0));

        assertThat(f.estado()).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(f.hastaCuandoCubre()).isNull();
        assertThat(f.venceEn()).isNull();
    }

    @Test void elEstadoDePagoSigueLaReglaUnicaConSusBordesExactos() {
        Instant vence = AHORA.minus(Duration.ofDays(2));

        assertThat(una(registro("A", 0, 30, AHORA.plusSeconds(1), 0)).estado()).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(una(registro("B", 0, 30, AHORA, 0)).estado()).as("el instante exacto del vencimiento").isEqualTo(EstadoSuscripcion.VENCIDA);
        assertThat(una(registro("C", 0, 30, AHORA, 3)).estado()).as("con gracia, el instante exacto ya es gracia").isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(una(registro("D", 0, 30, vence, 5)).estado()).isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(una(registro("E", 0, 30, vence, 2)).estado()).as("justo cuando se acaba la gracia").isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test void diceHastaCuandoSeLaSirveGraciaIncluida() {
        Instant vence = AHORA.plus(Duration.ofDays(10));

        Fila f = una(registro("Ana", 0, 300, vence, 5));

        assertThat(f.venceEn()).isEqualTo(vence);
        assertThat(f.diasDeGracia()).isEqualTo(5);
        assertThat(f.hastaCuandoCubre()).isEqualTo(vence.plus(Duration.ofDays(5)));
    }

    @Test void conservaElOrdenQueDaElRepositorio() {
        consumos.registros = List.of(registro("Zeta", 5, 30, null, 0), registro("Alfa", 20, 30, null, 0), registro("Mario", 1, 30, null, 0));

        assertThat(service.listar(OCTUBRE, FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE, 1, 20)).extracting(Fila::nombre).containsExactly("Zeta", "Alfa", "Mario");
        assertThat(service.todas(OCTUBRE, FiltroDeConsumo.TODAS, OrdenDeConsumo.PORCENTAJE)).extracting(Fila::nombre).containsExactly("Zeta", "Alfa", "Mario");
    }
}
