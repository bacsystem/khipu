package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.out.TopeDeDocumentosRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.*;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** #18: al llegar al tope de documentos del mes de su plan, la cuenta no emite más hasta el mes siguiente o hasta que le suban el plan. */
class TopeDelPlanServiceTest {
    final UUID cuentaId = UUID.randomUUID();
    final UUID empresa = UUID.randomUUID();
    final Fakes.Tenants tenants = new Fakes.Tenants();
    { tenants.asignarCuenta(empresa, cuentaId); }

    Limite tope = Limite.de(30);
    /** La bajada que espera su fecha, si hay (#18-H1). */
    CambiarPlanDeCuentaUseCase.Programado programado;
    final CambiarPlanDeCuentaUseCase planes = new CambiarPlanDeCuentaUseCase() {
        public PlanDeCuenta plan(UUID id) {
            Plan gratis = new Plan(UUID.randomUUID(), "Gratis", BigDecimal.ZERO, new Limites(tope, 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, true);
            Suscripcion s = new Suscripcion(UUID.randomUUID(), id, gratis.id(), Instant.parse("2026-10-01T05:00:00Z"), null, 0, null);
            return new PlanDeCuenta(id, gratis, s, EstadoSuscripcion.VIGENTE, null, programado);
        }
        public Previsualizacion previsualizar(UUID c, UUID p) { throw new AssertionError("solo se lee el plan"); }
        public PlanDeCuenta cambiar(ActorAdmin a, UUID c, UUID p, Instant v, Integer g) { throw new AssertionError("solo se lee el plan"); }
    };

    /** Lo ocupado por cuenta y mes, y qué cuentas se bloquearon al contar. */
    final Map<String, Long> ocupados = new HashMap<>();
    final List<UUID> bloqueadas = new ArrayList<>();
    final TopeDeDocumentosRepository repo = (cuenta, mes) -> {
        bloqueadas.add(cuenta);
        return ocupados.getOrDefault(cuenta + "/" + mes, 0L);
    };

    Clock reloj = Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneId.of("America/Lima"));
    TopeDelPlanService service() { return new TopeDelPlanService(tenants, planes, repo, reloj); }
    static final LocalDate OCTUBRE = LocalDate.of(2026, 10, 9);

    @Test void conLugarLibreDejaEmitirYBloqueaLaCuentaParaContar() {
        ocupados.put(cuentaId + "/2026-10", 29L);
        assertThatCode(() -> service().exigirDisponible(empresa, OCTUBRE)).doesNotThrowAnyException();
        assertThat(bloqueadas).containsExactly(cuentaId);
    }

    @Test void enElTopeExactoNoDejaEmitirYDiceCuandoSeRenueva() {
        ocupados.put(cuentaId + "/2026-10", 30L);
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("codigo", "LIMITE_PLAN")
                .hasMessageContaining("30 documentos")
                .hasMessageContaining("octubre de 2026")
                .hasMessageContaining("Gratis")
                .hasMessageContaining("1 de noviembre");
    }

    @Test void pasadoElTopeTampoco() {
        ocupados.put(cuentaId + "/2026-10", 31L);
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE)).hasFieldOrPropertyWithValue("codigo", "LIMITE_PLAN");
    }

    /** El mes es el de la fecha de emisión, como el consumo: una factura del 30 de setiembre emitida el 1 de octubre cuenta en setiembre. */
    @Test void cuentaEnElMesDeLaFechaDeEmision() {
        ocupados.put(cuentaId + "/2026-10", 30L);
        assertThatCode(() -> service().exigirDisponible(empresa, LocalDate.of(2026, 9, 30))).doesNotThrowAnyException();
    }

    @Test void unPlanSinTopeNoCuentaNiBloquea() {
        tope = Limite.sinLimite();
        ocupados.put(cuentaId + "/2026-10", 1_000_000L);
        assertThatCode(() -> service().exigirDisponible(empresa, OCTUBRE)).doesNotThrowAnyException();
        assertThat(bloqueadas).isEmpty();
    }

    /** Las empresas de integración no tienen cuenta ni plan: no se les aplica tope. */
    @Test void unaEmpresaSinCuentaNoTieneTope() {
        UUID integracion = UUID.randomUUID();
        assertThatCode(() -> service().exigirDisponible(integracion, OCTUBRE)).doesNotThrowAnyException();
        assertThat(bloqueadas).isEmpty();
    }

    @Test void elTopeEsDeLaCuentaNoDeOtra() {
        ocupados.put(UUID.randomUUID() + "/2026-10", 500L);
        assertThatCode(() -> service().exigirDisponible(empresa, OCTUBRE)).doesNotThrowAnyException();
    }

    /**
     * 328-H1: una bajada programada manda desde su fecha, aunque el worker que la aplica (cada 10 minutos) todavía no haya pasado. A las 00:05 del 1 de noviembre
     * la cuenta que bajó de 30 a 10 documentos ya tiene tope 10.
     */
    @Test void unaBajadaProgramadaMandaDesdeSuFechaAunqueTodaviaNoSeHayaAplicado() {
        Plan mini = new Plan(UUID.randomUUID(), "Mini", BigDecimal.ZERO, new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, true);
        programado = new CambiarPlanDeCuentaUseCase.Programado(mini, new CambioDePlan(mini.id(), Instant.parse("2026-11-01T05:00:00Z"), null, 0));
        ocupados.put(cuentaId + "/2026-11", 10L);
        LocalDate noviembre = LocalDate.of(2026, 11, 1);

        reloj = Clock.fixed(Instant.parse("2026-11-01T04:59:59Z"), ZoneId.of("America/Lima"));
        assertThatCode(() -> service().exigirDisponible(empresa, noviembre)).as("un segundo antes de su fecha manda el plan de hoy").doesNotThrowAnyException();

        reloj = Clock.fixed(Instant.parse("2026-11-01T05:05:00Z"), ZoneId.of("America/Lima"));
        assertThatThrownBy(() -> service().exigirDisponible(empresa, noviembre)).hasFieldOrPropertyWithValue("codigo", "LIMITE_PLAN").hasMessageContaining("Mini");
    }
}
