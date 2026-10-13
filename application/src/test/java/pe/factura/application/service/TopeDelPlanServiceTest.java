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
    /** Hasta cuándo está pagada la suscripción y su gracia (#285); sin vencimiento, como el plan gratis. */
    Instant venceEn;
    int diasDeGracia;
    final CambiarPlanDeCuentaUseCase planes = new CambiarPlanDeCuentaUseCase() {
        public PlanDeCuenta plan(UUID id) {
            Plan gratis = new Plan(UUID.randomUUID(), "Gratis", BigDecimal.ZERO, new Limites(tope, 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, true);
            Suscripcion s = new Suscripcion(UUID.randomUUID(), id, gratis.id(), Instant.parse("2026-09-01T05:00:00Z"), venceEn, diasDeGracia, null);
            return new PlanDeCuenta(id, gratis, s, s.estadoEn(reloj.instant()), s.hastaCuandoCubre(), programado);
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

    /**
     * #285: pagado hasta el 6 de octubre a las 00:00 de Lima, con 3 días de gracia, se sirve hasta el 9 a las 00:00 de Lima (05:00 UTC). Un segundo antes todavía
     * está en gracia y emite; en el instante exacto ya está vencido y no emite.
     */
    @Test void enGraciaEmiteHastaElUltimoSegundoYEnElBordeExactoYaNo() {
        venceEn = Instant.parse("2026-10-06T05:00:00Z");
        diasDeGracia = 3;

        reloj = Clock.fixed(Instant.parse("2026-10-09T04:59:59Z"), ZoneId.of("America/Lima"));
        assertThatCode(() -> service().exigirDisponible(empresa, OCTUBRE)).as("en gracia se sigue emitiendo").doesNotThrowAnyException();

        reloj = Clock.fixed(Instant.parse("2026-10-09T05:00:00Z"), ZoneId.of("America/Lima"));
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO")
                .hasMessageContaining("Gratis")
                .hasMessageContaining("8 de octubre");
    }

    /** Vencido no se emite aunque el plan no tenga tope. */
    @Test void vencidoNoEmiteAunqueElPlanNoTengaTope() {
        tope = Limite.sinLimite();
        venceEn = Instant.parse("2026-10-01T05:00:00Z");
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE)).hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO");
    }

    /**
     * 345-H1: el vencimiento se mira antes que el tope. Con lugar o en el tope, un plan vencido responde PLAN_VENCIDO (no LIMITE_PLAN, que le diría que vuelve a
     * emitir el mes que viene) y no cuenta lo ocupado: no toma el bloqueo de la cuenta para un rechazo que no depende de él.
     */
    @Test void vencidoConLugarOEnElTopeDiceQueVencioSinContarLoOcupado() {
        venceEn = Instant.parse("2026-10-01T05:00:00Z");

        ocupados.put(cuentaId + "/2026-10", 3L);
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE)).as("con lugar").hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO");

        ocupados.put(cuentaId + "/2026-10", 30L);
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE)).as("en el tope").hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO");

        assertThat(bloqueadas).as("no se contó ni se bloqueó la cuenta").isEmpty();
    }

    /**
     * 345-H3: si la bajada que ya manda también venció (el worker no la aplicó a tiempo), el mensaje dice cuándo terminó la gracia de esa suscripción nueva, no la de
     * la vieja: pagada hasta el 10 de noviembre (00:00 de Lima) con 2 días de gracia, se sirvió hasta el 11.
     */
    @Test void unaBajadaQueYaMandaYVencioDiceSuPropioUltimoDia() {
        venceEn = Instant.parse("2026-10-20T05:00:00Z");
        Plan mini = new Plan(UUID.randomUUID(), "Mini", BigDecimal.TEN, new Limites(Limite.de(30), 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, false);
        programado = new CambiarPlanDeCuentaUseCase.Programado(mini, new CambioDePlan(mini.id(), Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-11-10T05:00:00Z"), 2));

        reloj = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("America/Lima"));
        assertThatThrownBy(() -> service().exigirDisponible(empresa, LocalDate.of(2026, 11, 15)))
                .hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO")
                .hasMessageContaining("Mini")
                .hasMessageContaining("11 de noviembre");
    }

    /** Si ya llegó la fecha de una bajada programada, el estado de pago es el de esa suscripción nueva (aunque el worker todavía no la haya aplicado). */
    @Test void unaBajadaQueYaMandaTraeSuPropioVencimiento() {
        venceEn = Instant.parse("2026-10-20T05:00:00Z");
        Plan gratis = new Plan(UUID.randomUUID(), "Gratis", BigDecimal.ZERO, new Limites(Limite.de(30), 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, true);
        programado = new CambiarPlanDeCuentaUseCase.Programado(gratis, new CambioDePlan(gratis.id(), Instant.parse("2026-11-01T05:00:00Z"), null, 0));
        LocalDate noviembre = LocalDate.of(2026, 11, 1);

        reloj = Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneId.of("America/Lima"));
        assertThatThrownBy(() -> service().exigirDisponible(empresa, OCTUBRE)).as("antes de la bajada manda el plan vencido").hasFieldOrPropertyWithValue("codigo", "PLAN_VENCIDO");

        reloj = Clock.fixed(Instant.parse("2026-11-01T05:05:00Z"), ZoneId.of("America/Lima"));
        assertThatCode(() -> service().exigirDisponible(empresa, noviembre)).as("el gratis no vence").doesNotThrowAnyException();
    }
}
