package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.out.CupoDeCuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.*;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** #498: el plan limita API keys activas, usuarios y RUCs: el N-ésimo pasa, el N+1 se rechaza, y lo que ya excede el tope no se toca. */
class CupoDelPlanServiceTest {
    final UUID cuentaId = UUID.randomUUID();

    Limites limites = new Limites(Limite.de(30), 2, Limite.de(3), Limite.de(1), 1);
    /** La bajada que espera su fecha, si hay. */
    CambiarPlanDeCuentaUseCase.Programado programado;
    final CambiarPlanDeCuentaUseCase planes = new CambiarPlanDeCuentaUseCase() {
        public PlanDeCuenta plan(UUID id) {
            Plan plan = new Plan(UUID.randomUUID(), "Emprende", BigDecimal.ZERO, limites, EstadoPlan.ACTIVO, true);
            Suscripcion s = new Suscripcion(UUID.randomUUID(), id, plan.id(), Instant.parse("2026-09-01T05:00:00Z"), null, 0, null);
            return new PlanDeCuenta(id, plan, s, EstadoSuscripcion.VIGENTE, s.hastaCuandoCubre(), programado);
        }
        public Previsualizacion previsualizar(UUID c, UUID p) { throw new AssertionError("solo se lee el plan"); }
        public PlanDeCuenta cambiar(ActorAdmin a, UUID c, UUID p, Instant v, Integer g) { throw new AssertionError("solo se lee el plan"); }
    };

    /** Lo ocupado por recurso y el orden en que se bloqueó y se contó, para probar que el bloqueo va antes que el conteo. */
    final Map<RecursoDelPlan, Long> ocupados = new EnumMap<>(RecursoDelPlan.class);
    final List<String> orden = new ArrayList<>();
    final CupoDeCuentaRepository repo = new CupoDeCuentaRepository() {
        public void bloquear(UUID cuenta, RecursoDelPlan recurso) { orden.add("bloquear " + recurso); }
        public long ocupados(UUID cuenta, RecursoDelPlan recurso, Instant ahora) { orden.add("contar " + recurso); return ocupados.getOrDefault(recurso, 0L); }
        public UsoDeRecursos uso(UUID cuenta, Instant ahora) { throw new AssertionError("el cupo mira un recurso, no todos"); }
    };

    final Clock reloj = Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneId.of("America/Lima"));
    final CupoDelPlanService service = new CupoDelPlanService(planes, repo, reloj);

    @Test void elUltimoLugarLibreSePuedeTomar() {
        ocupados.put(RecursoDelPlan.USUARIOS, 2L);
        assertThatCode(() -> service.exigirLugar(cuentaId, RecursoDelPlan.USUARIOS)).doesNotThrowAnyException();
    }

    @Test void conElTopeExactoOcupadoSeRechazaConElCodigoYLoQueHayQueHacer() {
        ocupados.put(RecursoDelPlan.API_KEYS, 1L);
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.API_KEYS))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("codigo", "CUPO_DEL_PLAN")
                .hasMessageContaining("Emprende")
                .hasMessageContaining("incluye 1 API key")
                .hasMessageContaining("ya tienes 1 API key activas")
                .hasMessageContaining("Revoca");
    }

    @Test void cadaRecursoTieneSuPropioTope() {
        ocupados.put(RecursoDelPlan.API_KEYS, 1L);
        ocupados.put(RecursoDelPlan.USUARIOS, 3L);
        ocupados.put(RecursoDelPlan.RUCS, 1L);
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.API_KEYS)).hasFieldOrPropertyWithValue("codigo", "CUPO_DEL_PLAN");
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.USUARIOS)).hasMessageContaining("incluye 3 usuarios");
        assertThatCode(() -> service.exigirLugar(cuentaId, RecursoDelPlan.RUCS)).as("1 de 2 RUC: todavía cabe uno").doesNotThrowAnyException();
        ocupados.put(RecursoDelPlan.RUCS, 2L);
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.RUCS)).hasMessageContaining("incluye 2 RUCs").hasMessageContaining("cambio de plan");
    }

    /** Una cuenta con 2 usuarios en un plan de 1 sigue como está; solo se le niega uno más, y el mensaje dice la verdad (ya tiene 2). */
    @Test void loQueYaExcedeElTopeNoSeTocaPeroLoNuevoNoEntra() {
        limites = new Limites(Limite.de(30), 1, Limite.de(1), Limite.de(1), 1);
        ocupados.put(RecursoDelPlan.USUARIOS, 2L);
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.USUARIOS))
                .hasFieldOrPropertyWithValue("codigo", "CUPO_DEL_PLAN")
                .hasMessageContaining("incluye 1 usuario")
                .hasMessageContaining("ya tienes 2 usuarios (activos o con invitación pendiente)");
    }

    @Test void unPlanSinTopeNoCuentaNiBloquea() {
        limites = new Limites(Limite.sinLimite(), 1, Limite.sinLimite(), Limite.sinLimite(), 1);
        ocupados.put(RecursoDelPlan.API_KEYS, 1_000_000L);
        assertThatCode(() -> service.exigirLugar(cuentaId, RecursoDelPlan.API_KEYS)).doesNotThrowAnyException();
        assertThatCode(() -> service.exigirLugar(cuentaId, RecursoDelPlan.USUARIOS)).doesNotThrowAnyException();
        assertThat(orden).as("sin tope no hay nada que proteger").isEmpty();
    }

    /** El bloqueo va antes del conteo: dos altas a la vez no ven las dos el último lugar libre. */
    @Test void bloqueaElCupoAntesDeContar() {
        service.exigirLugar(cuentaId, RecursoDelPlan.USUARIOS);
        assertThat(orden).containsExactly("bloquear USUARIOS", "contar USUARIOS");
    }

    /** Una bajada cuya fecha ya llegó manda aunque el worker que la aplica no haya pasado todavía. */
    @Test void conUnaBajadaVencidaMandaElPlanNuevo() {
        Plan bajado = new Plan(UUID.randomUUID(), "Gratis", BigDecimal.ZERO, new Limites(Limite.de(30), 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, false);
        programado = new CambiarPlanDeCuentaUseCase.Programado(bajado, new CambioDePlan(bajado.id(), Instant.parse("2026-10-01T05:00:00Z"), null, 0));
        ocupados.put(RecursoDelPlan.API_KEYS, 1L);
        assertThatThrownBy(() -> service.exigirLugar(cuentaId, RecursoDelPlan.API_KEYS)).hasMessageContaining("Gratis");
    }
}
