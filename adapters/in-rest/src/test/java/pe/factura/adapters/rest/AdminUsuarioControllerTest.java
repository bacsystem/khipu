package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.SoporteDeAccesoUseCase;
import pe.factura.application.port.in.SoporteDeAccesoUseCase.Destinatario;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUsuarioController.class, properties = "app.portal-url=https://portal.khipu.test",
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminUsuarioControllerTest {
    static final UUID CUENTA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID USUARIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String RUTA = "/v1/admin/cuentas/" + CUENTA + "/usuarios/" + USUARIO;
    static final ActorAdmin CLAVE = ActorAdmin.clavePlataforma("127.0.0.1");

    @Autowired MockMvc mvc;
    @MockBean SoporteDeAccesoUseCase soporte;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder como(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE);
    }

    @Test void restablecerPasaElActorLaCuentaElUsuarioYLaUrlDelPortalYDiceAQuienSeMando() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = ActorAdmin.administrador(admin, "203.0.113.7");
        when(soporte.enviarRestablecimiento(actor, CUENTA, USUARIO, "https://portal.khipu.test")).thenReturn(new Destinatario(USUARIO, "ana@negocio.pe"));

        mvc.perform(post(RUTA + "/restablecimiento").requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.usuario_id").value(USUARIO.toString()))
                .andExpect(jsonPath("$.datos.correo").value("ana@negocio.pe"));
    }

    @Test void reenviarLaVerificacionPasaLoMismo() throws Exception {
        when(soporte.reenviarVerificacion(CLAVE, CUENTA, USUARIO, "https://portal.khipu.test")).thenReturn(new Destinatario(USUARIO, "ana@negocio.pe"));

        mvc.perform(como(post(RUTA + "/verificacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.usuario_id").value(USUARIO.toString()))
                .andExpect(jsonPath("$.datos.correo").value("ana@negocio.pe"));

        verify(soporte).reenviarVerificacion(CLAVE, CUENTA, USUARIO, "https://portal.khipu.test");
    }

    /** Nunca se muestra ni se fija una contraseña, y el enlace con su token no sale de la acción. */
    @Test void laRespuestaNoLlevaNingunaContrasenaNiEnlaceNiToken() throws Exception {
        when(soporte.enviarRestablecimiento(any(), eq(CUENTA), eq(USUARIO), any())).thenReturn(new Destinatario(USUARIO, "ana@negocio.pe"));

        String cuerpo = mvc.perform(como(post(RUTA + "/restablecimiento"))).andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("contrasena").doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("enlace").doesNotContain("/restablecer/").doesNotContainIgnoringCase("hash");
    }

    @Test void losErroresDelCasoDeUsoTienenSuEstado() throws Exception {
        record Caso(String ruta, String codigo, int estado) {}
        for (Caso c : new Caso[]{
                new Caso("/restablecimiento", "NO_ENCONTRADO", 404),
                new Caso("/restablecimiento", "USUARIO_INACTIVO", 409),
                new Caso("/restablecimiento", "CORREO_NO_CONFIGURADO", 503),
                new Caso("/restablecimiento", "CORREO_NO_ENVIADO", 502),
                new Caso("/verificacion", "CORREO_YA_VERIFICADO", 409),
                new Caso("/verificacion", "NO_ENCONTRADO", 404)}) {
            org.mockito.Mockito.reset(soporte);   // un mock que ya lanza no se puede reprogramar con `when`: ejecutaría la llamada
            when(soporte.enviarRestablecimiento(any(), any(), any(), any())).thenThrow(new DomainException(c.codigo(), "x"));
            when(soporte.reenviarVerificacion(any(), any(), any(), any())).thenThrow(new DomainException(c.codigo(), "x"));

            mvc.perform(como(post(RUTA + c.ruta())))
                    .andExpect(status().is(c.estado()))
                    .andExpect(jsonPath("$.codigo").value(c.codigo()));
        }
    }

    @Test void sinAdministradorNiClaveAutenticadosNoSeEjecutaNada() throws Exception {
        mvc.perform(post(RUTA + "/restablecimiento")).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA + "/verificacion")).andExpect(status().isUnauthorized());

        verifyNoInteractions(soporte);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinLlamarAlCasoDeUso() throws Exception {
        for (String ruta : new String[]{"/v1/admin/cuentas/no-es-un-uuid/usuarios/" + USUARIO, "/v1/admin/cuentas/" + CUENTA + "/usuarios/no-es-un-uuid"}) {
            mvc.perform(como(post(ruta + "/restablecimiento"))).andExpect(status().isBadRequest());
            mvc.perform(como(post(ruta + "/verificacion"))).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(soporte);
    }
}
