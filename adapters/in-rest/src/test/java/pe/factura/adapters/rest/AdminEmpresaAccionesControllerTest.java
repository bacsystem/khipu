package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.ApiKeyRevocada;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.CambioDeEntorno;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.Resultado;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.ResultadoDeConexion;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.tenant.Entorno;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cambiar el entorno, revocar una API key y probar la conexión de una empresa desde el backoffice (#187). */
@WebMvcTest(controllers = AdminEmpresaAccionesController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminEmpresaAccionesControllerTest {
    static final UUID ID = UUID.randomUUID();
    static final UUID KEY = UUID.randomUUID();
    static final String BASE = "/v1/admin/empresas/" + ID;

    @Autowired MockMvc mvc;
    @MockBean AccionesDeEmpresaUseCase acciones;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder conClave(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE);
    }

    // --- entorno ----------------------------------------------------------------------------------------------------------------------

    @Test void cambiarElEntornoPasaElActorYDiceDeCuantoAcuanto() throws Exception {
        UUID admin = UUID.randomUUID();
        when(acciones.cambiarEntorno(ActorAdmin.administrador(admin, "203.0.113.7"), ID, Entorno.PRODUCCION)).thenReturn(new CambioDeEntorno(ID, Entorno.BETA, Entorno.PRODUCCION));

        mvc.perform(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"PRODUCCION\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.empresa_id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.desde").value("BETA"))
                .andExpect(jsonPath("$.datos.hacia").value("PRODUCCION"));
    }

    @Test void unEntornoQueNoExisteEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(conClave(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"PRUEBAS\"}"))).andExpect(status().isBadRequest());

        verifyNoInteractions(acciones);
    }

    @Test void sinCuerpoElCasoDeUsoRechazaElEntornoNulo() throws Exception {
        when(acciones.cambiarEntorno(any(), eq(ID), eq(null))).thenThrow(new DomainException("ENTORNO_INVALIDO", "El entorno es obligatorio"));

        mvc.perform(conClave(post(BASE + "/entorno")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ENTORNO_INVALIDO"));
    }

    @Test void losConflictosDelEntornoSon409ConSuCodigo() throws Exception {
        when(acciones.cambiarEntorno(any(), eq(ID), eq(Entorno.BETA))).thenThrow(new DomainException("ENTORNO_SIN_CAMBIOS", "La empresa ya está en BETA"));
        when(acciones.cambiarEntorno(any(), eq(ID), eq(Entorno.PRODUCCION))).thenThrow(new DomainException("EMPRESA_CON_ENVIOS_PENDIENTES", "pendientes"));

        mvc.perform(conClave(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"BETA\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ENTORNO_SIN_CAMBIOS"));
        mvc.perform(conClave(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"PRODUCCION\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EMPRESA_CON_ENVIOS_PENDIENTES"));
    }

    // --- API keys ---------------------------------------------------------------------------------------------------------------------

    @Test void revocarUnaKeyPasaElActorYLosDosIdsYDiceCualYCuando() throws Exception {
        UUID admin = UUID.randomUUID();
        when(acciones.revocarApiKey(ActorAdmin.administrador(admin, "203.0.113.7"), ID, KEY)).thenReturn(new ApiKeyRevocada(KEY, Instant.parse("2026-10-03T09:00:00Z")));

        mvc.perform(post(BASE + "/api-keys/" + KEY + "/revocar").requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.api_key_id").value(KEY.toString()))
                .andExpect(jsonPath("$.datos.revocada_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void revocarUnaKeyYaRevocadaEsConflictoYUnaQueNoExisteEs404() throws Exception {
        when(acciones.revocarApiKey(any(), eq(ID), eq(KEY))).thenThrow(new DomainException("API_KEY_YA_REVOCADA", "La API key ya estaba revocada"));
        UUID otra = UUID.randomUUID();
        when(acciones.revocarApiKey(any(), eq(ID), eq(otra))).thenThrow(new DomainException("NO_ENCONTRADO", "La API key no existe"));

        mvc.perform(conClave(post(BASE + "/api-keys/" + KEY + "/revocar"))).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("API_KEY_YA_REVOCADA"));
        mvc.perform(conClave(post(BASE + "/api-keys/" + otra + "/revocar"))).andExpect(status().isNotFound());
    }

    // --- probar la conexión -----------------------------------------------------------------------------------------------------------

    @Test void probarLaConexionDiceCómoContestoSunat() throws Exception {
        UUID admin = UUID.randomUUID();
        when(acciones.probarConexion(ActorAdmin.administrador(admin, "203.0.113.7"), ID)).thenReturn(new ResultadoDeConexion(Resultado.RECHAZADO, Entorno.BETA, "1033", "El ticket no existe"));

        mvc.perform(post(BASE + "/prueba-de-conexion").requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("RECHAZADO"))
                .andExpect(jsonPath("$.datos.entorno").value("BETA"))
                .andExpect(jsonPath("$.datos.codigo").value("1033"))
                .andExpect(jsonPath("$.datos.mensaje").value("El ticket no existe"));
    }

    @Test void siSunatContestaConNormalidadNoHayCodigoNiMensaje() throws Exception {
        when(acciones.probarConexion(any(), eq(ID))).thenReturn(new ResultadoDeConexion(Resultado.CONECTADO, Entorno.PRODUCCION, null, null));

        mvc.perform(conClave(post(BASE + "/prueba-de-conexion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("CONECTADO"))
                .andExpect(jsonPath("$.datos.codigo").doesNotExist())
                .andExpect(jsonPath("$.datos.mensaje").doesNotExist());
    }

    @Test void sinCredencialesSolLaPruebaEsConflicto() throws Exception {
        when(acciones.probarConexion(any(), eq(ID))).thenThrow(new DomainException("SOL_NO_CARGADAS", "La empresa no tiene credenciales SOL cargadas"));

        mvc.perform(conClave(post(BASE + "/prueba-de-conexion"))).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SOL_NO_CARGADAS"));
    }

    // --- común ------------------------------------------------------------------------------------------------------------------------

    @Test void unaEmpresaQueNoExisteEsNoEncontradaEnLasTresAcciones() throws Exception {
        when(acciones.cambiarEntorno(any(), eq(ID), any())).thenThrow(new DomainException("NO_ENCONTRADO", "La empresa no existe"));
        when(acciones.revocarApiKey(any(), eq(ID), any())).thenThrow(new DomainException("NO_ENCONTRADO", "La empresa no existe"));
        when(acciones.probarConexion(any(), eq(ID))).thenThrow(new DomainException("NO_ENCONTRADO", "La empresa no existe"));

        mvc.perform(conClave(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"BETA\"}"))).andExpect(status().isNotFound());
        mvc.perform(conClave(post(BASE + "/api-keys/" + KEY + "/revocar"))).andExpect(status().isNotFound());
        mvc.perform(conClave(post(BASE + "/prueba-de-conexion"))).andExpect(status().isNotFound());
    }

    @Test void sinAdministradorNiClaveAutenticadosNoSeEjecutaNada() throws Exception {
        mvc.perform(post(BASE + "/entorno").contentType("application/json").content("{\"entorno\":\"BETA\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/api-keys/" + KEY + "/revocar")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/prueba-de-conexion")).andExpect(status().isUnauthorized());

        verifyNoInteractions(acciones);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinHacerNada() throws Exception {
        mvc.perform(conClave(post("/v1/admin/empresas/no-es-un-uuid/entorno").contentType("application/json").content("{\"entorno\":\"BETA\"}"))).andExpect(status().isBadRequest());
        mvc.perform(conClave(post("/v1/admin/empresas/no-es-un-uuid/api-keys/" + KEY + "/revocar"))).andExpect(status().isBadRequest());
        mvc.perform(conClave(post(BASE + "/api-keys/no-es-un-uuid/revocar"))).andExpect(status().isBadRequest());
        mvc.perform(conClave(post("/v1/admin/empresas/no-es-un-uuid/prueba-de-conexion"))).andExpect(status().isBadRequest());

        verifyNoInteractions(acciones);
    }

    @Test void elCasoDeUsoRecibeElActorQueSeAutentico() throws Exception {
        when(acciones.probarConexion(any(), eq(ID))).thenReturn(new ResultadoDeConexion(Resultado.CONECTADO, Entorno.BETA, null, null));

        mvc.perform(conClave(post(BASE + "/prueba-de-conexion"))).andExpect(status().isOk());

        verify(acciones).probarConexion(ActorAdmin.clavePlataforma("127.0.0.1"), ID);
    }
}
