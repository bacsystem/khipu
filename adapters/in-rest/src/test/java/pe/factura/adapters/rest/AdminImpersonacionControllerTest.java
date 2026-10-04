package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase.Impersonacion;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Impersonar a un usuario desde el backoffice (#184). */
@WebMvcTest(controllers = AdminImpersonacionController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminImpersonacionControllerTest {
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID USUARIO = UUID.randomUUID();
    static final String RUTA = "/v1/admin/cuentas/" + CUENTA + "/usuarios/" + USUARIO + "/impersonar";
    static final Usuario ANA = new Usuario(USUARIO, CUENTA, "ana@negocio.pe", "hash", Rol.ADMIN, true, Instant.parse("2026-09-01T10:00:00Z"));

    @Autowired MockMvc mvc;
    @MockBean ImpersonarUsuarioUseCase impersonar;

    @Test void devuelveElTokenDeLaSesionDeSoporteSuExpiracionYAQuienSeMira() throws Exception {
        UUID admin = UUID.randomUUID();
        when(impersonar.impersonar(ActorAdmin.administrador(admin, "203.0.113.7"), CUENTA, USUARIO)).thenReturn(new Impersonacion("token-de-soporte", Instant.parse("2026-10-04T12:15:00Z"), ANA));

        mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access_token").value("token-de-soporte"))
                .andExpect(jsonPath("$.datos.expira_en").value("2026-10-04T12:15:00Z"))
                .andExpect(jsonPath("$.datos.usuario.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.usuario.id").value(USUARIO.toString()));
    }

    /** El token es una credencial: ningún intermediario debe guardar esta respuesta. */
    @Test void laRespuestaNoSeGuardaEnNingunaCache() throws Exception {
        when(impersonar.impersonar(any(), eq(CUENTA), eq(USUARIO))).thenReturn(new Impersonacion("t", Instant.parse("2026-10-04T12:15:00Z"), ANA));

        mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test void laRespuestaNoLlevaElHashDeLaContrasena() throws Exception {
        when(impersonar.impersonar(any(), eq(CUENTA), eq(USUARIO))).thenReturn(new Impersonacion("t", Instant.parse("2026-10-04T12:15:00Z"), ANA));

        String cuerpo = mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID())).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("hash").doesNotContain("password");
    }

    @Test void laClaveDePlataformaNoPuedeImpersonar() throws Exception {
        when(impersonar.impersonar(any(), eq(CUENTA), eq(USUARIO))).thenThrow(new DomainException("REQUIERE_ADMINISTRADOR", "requiere la sesión de un administrador"));

        mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("REQUIERE_ADMINISTRADOR"));
    }

    @Test void losErroresDelCasoDeUsoTienenSuEstado() throws Exception {
        when(impersonar.impersonar(any(), eq(CUENTA), eq(USUARIO)))
                .thenThrow(new DomainException("NO_ENCONTRADO", "El usuario no existe en esta cuenta"))
                .thenThrow(new DomainException("USUARIO_INACTIVO", "desactivado"));

        mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post(RUTA).requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
    }

    @Test void sinAdministradorNiClaveAutenticadosNoSeEjecutaNada() throws Exception {
        mvc.perform(post(RUTA)).andExpect(status().isUnauthorized());

        verifyNoInteractions(impersonar);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinHacerNada() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/usuarios/" + USUARIO + "/impersonar").requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/cuentas/" + CUENTA + "/usuarios/no-es-un-uuid/impersonar").requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID())).andExpect(status().isBadRequest());

        verifyNoInteractions(impersonar);
    }
}
