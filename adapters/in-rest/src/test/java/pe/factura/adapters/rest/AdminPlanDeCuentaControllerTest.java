package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Efecto;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Previsualizacion;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Programado;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.DireccionDeCambio;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El plan de una cuenta desde el backoffice (#191): ver, previsualizar y cambiar. */
@WebMvcTest(controllers = AdminPlanDeCuentaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminPlanDeCuentaControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID PLAN = UUID.randomUUID();
    static final Instant VENCE = Instant.parse("2026-11-01T05:00:00Z");
    static final Instant CICLO = Instant.parse("2026-10-01T05:00:00Z");
    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.sinLimite(), 5);

    @Autowired MockMvc mvc;
    @MockBean CambiarPlanDeCuentaUseCase planes;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static Plan plan(String nombre, String precio) { return new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), LIMITES, EstadoPlan.ACTIVO, false); }

    static PlanDeCuenta vista(Programado programado) {
        Plan emprende = new Plan(PLAN, "Emprende", new BigDecimal("29"), LIMITES, EstadoPlan.ACTIVO, false);
        Suscripcion s = new Suscripcion(UUID.randomUUID(), CUENTA, PLAN, Instant.parse("2026-09-01T10:00:00Z"), VENCE, 5, null);
        return new PlanDeCuenta(CUENTA, emprende, s, EstadoSuscripcion.VIGENTE, s.hastaCuandoCubre(), programado);
    }

    @Test void elPlanDeUnaCuentaDiceCualEsSuEstadoYHastaCuandoCubre() throws Exception {
        when(planes.plan(CUENTA)).thenReturn(vista(null));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.plan.id").value(PLAN.toString()))
                .andExpect(jsonPath("$.datos.plan.nombre").value("Emprende"))
                .andExpect(jsonPath("$.datos.plan.precio_mensual").value(29.0))
                .andExpect(jsonPath("$.datos.plan.limites.documentos_al_mes.maximo").value(300))
                .andExpect(jsonPath("$.datos.plan.limites.usuarios.ilimitado").value(false))
                .andExpect(jsonPath("$.datos.plan.limites.api_keys.ilimitado").value(true))
                .andExpect(jsonPath("$.datos.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.datos.inicia_en").value("2026-09-01T10:00:00Z"))
                .andExpect(jsonPath("$.datos.vence_en").value("2026-11-01T05:00:00Z"))
                .andExpect(jsonPath("$.datos.dias_de_gracia").value(5))
                .andExpect(jsonPath("$.datos.hasta_cuando_cubre").value("2026-11-06T05:00:00Z"))
                .andExpect(jsonPath("$.datos.programado").doesNotExist());
    }

    @Test void unaBajadaProgramadaSeVeAparte() throws Exception {
        Plan gratis = plan("Gratis", "0");
        when(planes.plan(CUENTA)).thenReturn(vista(new Programado(gratis, new CambioDePlan(gratis.id(), CICLO, null, 0))));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.plan.nombre").value("Emprende"))
                .andExpect(jsonPath("$.datos.programado.plan.nombre").value("Gratis"))
                .andExpect(jsonPath("$.datos.programado.aplica_desde").value("2026-10-01T05:00:00Z"))
                .andExpect(jsonPath("$.datos.programado.vence_en").doesNotExist())
                .andExpect(jsonPath("$.datos.programado.dias_de_gracia").value(0));
    }

    @Test void unPlanSinVencimientoNoLoDiceNiDiceHastaCuandoCubre() throws Exception {
        Plan gratis = plan("Gratis", "0");
        Suscripcion s = new Suscripcion(UUID.randomUUID(), CUENTA, gratis.id(), Instant.parse("2026-09-01T10:00:00Z"), null, 0, null);
        when(planes.plan(CUENTA)).thenReturn(new PlanDeCuenta(CUENTA, gratis, s, EstadoSuscripcion.VIGENTE, null, null));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vence_en").doesNotExist())
                .andExpect(jsonPath("$.datos.hasta_cuando_cubre").doesNotExist());
    }

    @Test void laPrevisualizacionDiceCuandoEntraYQuePasaConElConsumoDelMes() throws Exception {
        Plan actual = plan("Negocio", "69");
        Plan nuevo = plan("Emprende", "29");
        Plan antes = plan("Gratis", "0");
        when(planes.previsualizar(CUENTA, nuevo.id())).thenReturn(new Previsualizacion(CUENTA, actual, nuevo, DireccionDeCambio.BAJADA, Efecto.CICLO_SIGUIENTE, CICLO,
                YearMonth.of(2026, 9), 800, Limite.de(300), true,
                new CambiarPlanDeCuentaUseCase.Programado(antes, new pe.factura.domain.plan.CambioDePlan(antes.id(), CICLO, null, 0))));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan/previsualizacion").param("plan_id", nuevo.id().toString()).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.plan_actual.nombre").value("Negocio"))
                .andExpect(jsonPath("$.datos.plan_nuevo.nombre").value("Emprende"))
                .andExpect(jsonPath("$.datos.direccion").value("BAJADA"))
                .andExpect(jsonPath("$.datos.efecto").value("CICLO_SIGUIENTE"))
                .andExpect(jsonPath("$.datos.aplica_desde").value("2026-10-01T05:00:00Z"))
                .andExpect(jsonPath("$.datos.mes").value("2026-09"))
                .andExpect(jsonPath("$.datos.consumo_del_mes").value(800))
                .andExpect(jsonPath("$.datos.limite_de_documentos.maximo").value(300))
                .andExpect(jsonPath("$.datos.supera_el_limite").value(true))
                .andExpect(jsonPath("$.datos.programado_que_se_descarta.plan.nombre").value("Gratis"))
                .andExpect(jsonPath("$.datos.programado_que_se_descarta.aplica_desde").value("2026-10-01T05:00:00Z"));
    }

    @Test void laPrevisualizacionSinNadaQueDescartarNoLoMenciona() throws Exception {
        Plan actual = plan("Emprende", "29");
        Plan nuevo = plan("Negocio", "69");
        when(planes.previsualizar(CUENTA, nuevo.id())).thenReturn(new Previsualizacion(CUENTA, actual, nuevo, DireccionDeCambio.SUBIDA, Efecto.INMEDIATO, CICLO,
                YearMonth.of(2026, 9), 10, Limite.de(1500), false, null));

        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan/previsualizacion").param("plan_id", nuevo.id().toString()).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.programado_que_se_descarta").doesNotExist());
    }

    @Test void laPrevisualizacionExigeElPlan() throws Exception {
        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan/previsualizacion").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/cuentas/" + CUENTA + "/plan/previsualizacion").param("plan_id", "no-es-un-uuid").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(planes);
    }

    @Test void cambiarPasaElActorElPlanElVencimientoYLaGracia() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = ActorAdmin.administrador(admin, "203.0.113.7");
        when(planes.cambiar(any(), any(), any(), any(), any())).thenReturn(vista(null));

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json")
                        .content("{\"plan_id\":\"" + PLAN + "\",\"vence_en\":\"2026-11-01T05:00:00Z\",\"dias_de_gracia\":5}")
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.plan.nombre").value("Emprende"));

        verify(planes).cambiar(actor, CUENTA, PLAN, VENCE, 5);
    }

    @Test void sinVencimientoNiGraciaSeMandanNulos() throws Exception {
        when(planes.cambiar(any(), any(), any(), any(), any())).thenReturn(vista(null));

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content("{\"plan_id\":\"" + PLAN + "\"}").with(clave())).andExpect(status().isOk());

        verify(planes).cambiar(ActorAdmin.clavePlataforma("127.0.0.1"), CUENTA, PLAN, null, null);
    }

    @Test void sinPlanElCuerpoSeRechazaSinCambiarNada() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content("{}").with(clave())).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content("{\"plan_id\":\"no-es-un-uuid\"}").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(planes);
    }

    @Test void unVencimientoQueNoEsUnaFechaEs400() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content("{\"plan_id\":\"" + PLAN + "\",\"vence_en\":\"mañana\"}").with(clave()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(planes);
    }

    @Test void losRechazosDelDominioTienenSuCodigoHttp() throws Exception {
        when(planes.cambiar(any(), eq(CUENTA), any(), any(), any()))
                .thenThrow(new DomainException("PLAN_INACTIVO", "fuera de la oferta"))
                .thenThrow(new DomainException("CAMBIO_CONCURRENTE", "otro administrador"))
                .thenThrow(new DomainException("VENCIMIENTO_REQUERIDO", "falta"))
                .thenThrow(new DomainException("GRACIA_INVALIDA", "mala"))
                .thenThrow(new DomainException("NO_ENCONTRADO", "no existe"));
        String cuerpo = "{\"plan_id\":\"" + PLAN + "\"}";

        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content(cuerpo).with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAN_INACTIVO"));
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content(cuerpo).with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAMBIO_CONCURRENTE"));
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content(cuerpo).with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VENCIMIENTO_REQUERIDO"));
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content(cuerpo).with(clave())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("GRACIA_INVALIDA"));
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content(cuerpo).with(clave())).andExpect(status().isNotFound());
    }

    @Test void sinAdministradorNiClaveAutenticadosElCambioNoSeEjecuta() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/plan").contentType("application/json").content("{\"plan_id\":\"" + PLAN + "\"}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(planes);
    }

    @Test void unIdentificadorDeCuentaQueNoEsUnUuidEs400SinHacerNada() throws Exception {
        mvc.perform(get("/v1/admin/cuentas/no-es-un-uuid/plan").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/cuentas/no-es-un-uuid/plan/previsualizacion").param("plan_id", PLAN.toString()).with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/plan").contentType("application/json").content("{\"plan_id\":\"" + PLAN + "\"}").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(planes);
    }
}
