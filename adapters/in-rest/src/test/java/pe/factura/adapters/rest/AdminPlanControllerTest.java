package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.GestionarPlanesUseCase;
import pe.factura.application.port.in.GestionarPlanesUseCase.DatosDePlan;
import pe.factura.application.port.in.GestionarPlanesUseCase.PlanConUso;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDeLimites;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Planes desde el backoffice (#190): cómo se piden, cómo se responden y qué código HTTP tiene cada rechazo. */
@WebMvcTest(controllers = AdminPlanController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminPlanControllerTest {
    static final UUID ID = UUID.randomUUID();
    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.sinLimite(), 5);
    static final String CUERPO = """
            {"nombre":"Estudio","precio_mensual":49.90,"limites":{"documentos_al_mes":{"maximo":300},"rucs":1,"usuarios":{"maximo":1},"api_keys":{"ilimitado":true},"retencion_anios":5}}""";

    @Autowired MockMvc mvc;
    @MockBean GestionarPlanesUseCase planes;

    static PlanConUso plan(long cuentas) {
        return new PlanConUso(new Plan(ID, "Estudio", new BigDecimal("49.90"), LIMITES, EstadoPlan.ACTIVO, false), cuentas);
    }

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    // --- listar -----------------------------------------------------------------------------------------------------------------------------

    @Test void listaLosPlanesConSusLimitesElPrecioYCuantasCuentasLosUsan() throws Exception {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.sinLimite(), 5);
        Plan conCambio = new Plan(ID, "Estudio", new BigDecimal("49.90"), LIMITES, EstadoPlan.ACTIVO, true, new CambioDeLimites(mas, Instant.parse("2026-11-01T05:00:00Z")));
        when(planes.listar()).thenReturn(List.of(new PlanConUso(conCambio, 12)));

        mvc.perform(get("/v1/admin/planes").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.datos[0].nombre").value("Estudio"))
                .andExpect(jsonPath("$.datos[0].precio_mensual").value(49.90))
                .andExpect(jsonPath("$.datos[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos[0].por_defecto").value(true))
                .andExpect(jsonPath("$.datos[0].cuentas").value(12))
                .andExpect(jsonPath("$.datos[0].limites.documentos_al_mes.maximo").value(300))
                .andExpect(jsonPath("$.datos[0].limites.documentos_al_mes.ilimitado").value(false))
                .andExpect(jsonPath("$.datos[0].limites.rucs").value(1))
                .andExpect(jsonPath("$.datos[0].limites.usuarios.maximo").value(1))
                .andExpect(jsonPath("$.datos[0].limites.api_keys.ilimitado").value(true))
                .andExpect(jsonPath("$.datos[0].limites.api_keys.maximo").doesNotExist())
                .andExpect(jsonPath("$.datos[0].limites.retencion_anios").value(5))
                .andExpect(jsonPath("$.datos[0].limites_programados.aplica_desde").value("2026-11-01T05:00:00Z"))
                .andExpect(jsonPath("$.datos[0].limites_programados.limites.documentos_al_mes.maximo").value(900));
    }

    @Test void unPlanSinCambioProgramadoNoTraeEseCampo() throws Exception {
        when(planes.listar()).thenReturn(List.of(plan(0)));

        mvc.perform(get("/v1/admin/planes").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].limites_programados").doesNotExist());
    }

    // --- crear ------------------------------------------------------------------------------------------------------------------------------

    @Test void crearPasaElActorYLosDatosYResponde201() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = ActorAdmin.administrador(admin, "203.0.113.7");
        when(planes.crear(eq(actor), any())).thenReturn(plan(0));

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.cuentas").value(0));

        verify(planes).crear(actor, new DatosDePlan("Estudio", new BigDecimal("49.90"), LIMITES));
    }

    /** H20: un plan a medida se pide fuera de la publicidad, y la respuesta dice si se publica. */
    @Test void laVisibilidadEnPublicidadViajaEnElCuerpoYEnLaRespuesta() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = ActorAdmin.administrador(admin, "203.0.113.7");
        Plan aMedida = new Plan(ID, "Estudio", new BigDecimal("49.90"), LIMITES, EstadoPlan.ACTIVO, false).conVisibilidadEnPublicidad(false);
        when(planes.crear(eq(actor), any())).thenReturn(new PlanConUso(aMedida, 0));

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO.replace("}}", "},\"visible_en_publicidad\":false}"))
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.visible_en_publicidad").value(false));

        verify(planes).crear(actor, new DatosDePlan("Estudio", new BigDecimal("49.90"), LIMITES, false));
    }

    @Test void unLimiteOmitidoNoSeVuelveIlimitadoEnSilencio() throws Exception {
        String sinDocumentos = CUERPO.replace("\"documentos_al_mes\":{\"maximo\":300},", "");

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(sinDocumentos).with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));

        verifyNoInteractions(planes);
    }

    @Test void unLimiteSinMaximoNiIlimitadoSeRechaza() throws Exception {
        String vacio = CUERPO.replace("{\"maximo\":300}", "{}");

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(vacio).with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));

        verifyNoInteractions(planes);
    }

    @Test void unLimiteIlimitadoYConMaximoALaVezEsAmbiguo() throws Exception {
        String ambiguo = CUERPO.replace("{\"maximo\":300}", "{\"maximo\":300,\"ilimitado\":true}");

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(ambiguo).with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));

        verifyNoInteractions(planes);
    }

    @Test void sinLimitesOSinRucOSinRetencionSeRechaza() throws Exception {
        mvc.perform(post("/v1/admin/planes").contentType("application/json").content("{\"nombre\":\"X\",\"precio_mensual\":1}").with(clave()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));
        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO.replace("\"rucs\":1,", "")).with(clave()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));
        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO.replace(",\"retencion_anios\":5", "")).with(clave()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("RETENCION_INVALIDA"));

        verifyNoInteractions(planes);
    }

    @Test void unPlanConElNombreRepetidoEsConflicto() throws Exception {
        when(planes.crear(any(), any())).thenThrow(new DomainException("NOMBRE_DUPLICADO", "Ya existe un plan llamado «Estudio»"));

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO).with(clave()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"));
    }

    @Test void losDatosInvalidosDelDominioSonUnprocessable() throws Exception {
        when(planes.crear(any(), any())).thenThrow(new DomainException("PRECIO_INVALIDO", "El precio mensual debe ser cero o más"));

        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO).with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRECIO_INVALIDO"));
    }

    // --- editar -----------------------------------------------------------------------------------------------------------------------------

    @Test void editarPasaElIdYLosDatos() throws Exception {
        when(planes.editar(any(), eq(ID), any())).thenReturn(plan(3));

        mvc.perform(put("/v1/admin/planes/" + ID).contentType("application/json").content(CUERPO).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuentas").value(3));

        verify(planes).editar(ActorAdmin.clavePlataforma("127.0.0.1"), ID, new DatosDePlan("Estudio", new BigDecimal("49.90"), LIMITES));
    }

    @Test void editarUnPlanQueNoExisteEsNoEncontrado() throws Exception {
        when(planes.editar(any(), eq(ID), any())).thenThrow(new DomainException("NO_ENCONTRADO", "El plan no existe"));

        mvc.perform(put("/v1/admin/planes/" + ID).contentType("application/json").content(CUERPO).with(clave())).andExpect(status().isNotFound());
    }

    // --- desactivar, activar, borrar --------------------------------------------------------------------------------------------------------

    @Test void desactivarYActivarPasanElActor() throws Exception {
        when(planes.desactivar(any(), eq(ID))).thenReturn(plan(2));
        when(planes.activar(any(), eq(ID))).thenReturn(plan(2));

        mvc.perform(post("/v1/admin/planes/" + ID + "/desactivar").with(clave())).andExpect(status().isOk()).andExpect(jsonPath("$.datos.cuentas").value(2));
        mvc.perform(post("/v1/admin/planes/" + ID + "/activar").with(clave())).andExpect(status().isOk());

        verify(planes).desactivar(ActorAdmin.clavePlataforma("127.0.0.1"), ID);
        verify(planes).activar(ActorAdmin.clavePlataforma("127.0.0.1"), ID);
    }

    @Test void borrarResponde200ConElSobreYPasaElActor() throws Exception {
        mvc.perform(delete("/v1/admin/planes/" + ID).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("exito"));

        verify(planes).eliminar(ActorAdmin.clavePlataforma("127.0.0.1"), ID);
    }

    @Test void losConflictosDeEstadoSon409() throws Exception {
        when(planes.desactivar(any(), eq(ID))).thenThrow(new DomainException("PLAN_POR_DEFECTO", "no se puede"));
        when(planes.activar(any(), eq(ID))).thenThrow(new DomainException("PLAN_YA_ACTIVO", "ya"));
        org.mockito.Mockito.doThrow(new DomainException("PLAN_EN_USO", "lo tienen 2 cuentas")).when(planes).eliminar(any(), eq(ID));

        mvc.perform(post("/v1/admin/planes/" + ID + "/desactivar").with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAN_POR_DEFECTO"));
        mvc.perform(post("/v1/admin/planes/" + ID + "/activar").with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAN_YA_ACTIVO"));
        mvc.perform(delete("/v1/admin/planes/" + ID).with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAN_EN_USO"));
    }

    @Test void yaInactivoEsConflicto() throws Exception {
        when(planes.desactivar(any(), eq(ID))).thenThrow(new DomainException("PLAN_YA_INACTIVO", "ya"));

        mvc.perform(post("/v1/admin/planes/" + ID + "/desactivar").with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PLAN_YA_INACTIVO"));
    }

    // --- quién puede ------------------------------------------------------------------------------------------------------------------------

    @Test void sinAdministradorNiClaveAutenticadosNingunaAccionSeEjecuta() throws Exception {
        mvc.perform(post("/v1/admin/planes").contentType("application/json").content(CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/admin/planes/" + ID).contentType("application/json").content(CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/planes/" + ID + "/desactivar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/planes/" + ID + "/activar")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/admin/planes/" + ID)).andExpect(status().isUnauthorized());

        verifyNoInteractions(planes);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinHacerNada() throws Exception {
        mvc.perform(put("/v1/admin/planes/no-es-un-uuid").contentType("application/json").content(CUERPO).with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/planes/no-es-un-uuid/desactivar").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/planes/no-es-un-uuid/activar").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(delete("/v1/admin/planes/no-es-un-uuid").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(planes);
    }
}
