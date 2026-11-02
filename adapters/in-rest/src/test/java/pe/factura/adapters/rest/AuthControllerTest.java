package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AceptarTerminosUseCase;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.AceptacionDeTerminos;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code app.registro-abierto} se fija explícito: el default real (issue #174) es cerrado, ver {@link AuthControllerRegistroCerradoTest}. */
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, IpDelCliente.class})
@TestPropertySource(properties = "app.registro-abierto=true")
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AutenticarUsuarioUseCase auth;
    @MockitoBean AceptarTerminosUseCase terminos;

    UUID usuarioId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    Usuario usuario = new Usuario(usuarioId, cuentaId, "ana@negocio.pe", "hash", Rol.ADMIN, true);
    AutenticarUsuarioUseCase.Tokens tokens = new AutenticarUsuarioUseCase.Tokens("access-token", "refresh-token", usuario, false);

    @Test void registroDevuelve201ConTokens() throws Exception {
        when(auth.registrar("Mi negocio", "ana@negocio.pe", "Segura123", "987654321", true, "http://localhost:3000")).thenReturn(tokens);
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.access").value("access-token"))
                .andExpect(jsonPath("$.datos.refresh").value("refresh-token"))
                .andExpect(jsonPath("$.datos.usuario.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.usuario.rol").value("ADMIN"))
                .andExpect(jsonPath("$.datos.usuario.terminos_pendientes").value(false));
        verify(auth).registrar("Mi negocio", "ana@negocio.pe", "Segura123", "987654321", true, "http://localhost:3000");
    }

    // --- #342: términos del servicio y política de privacidad ------------------------------------------------------------------------

    /** Sin el campo, o con {@code false}, el caso de uso recibe {@code false} y responde TERMINOS_NO_ACEPTADOS: un 400, no un 422 de validación. */
    @Test void registroSinAceptarLosTerminosEs400() throws Exception {
        when(auth.registrar(anyString(), anyString(), anyString(), anyString(), eq(false), anyString()))
                .thenThrow(new DomainException("TERMINOS_NO_ACEPTADOS", "Para crear la cuenta tienes que aceptar los términos"));
        for (String acepta : new String[]{"", ",\"acepta_terminos\":false", ",\"acepta_terminos\":null"}) {
            mvc.perform(post("/v1/auth/registro").contentType("application/json")
                            .content("{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"" + acepta + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("TERMINOS_NO_ACEPTADOS"));
        }
        verify(auth, org.mockito.Mockito.times(3)).registrar("Mi negocio", "ana@negocio.pe", "Segura123", "987654321", false, "http://localhost:3000");
    }

    @Test void elLoginDiceSiFaltaAceptarLosTerminos() throws Exception {
        when(auth.login("ana@negocio.pe", "Segura123", null)).thenReturn(new AutenticarUsuarioUseCase.Tokens("a", "r", usuario, true));
        mvc.perform(post("/v1/auth/login").contentType("application/json").content("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.usuario.terminos_pendientes").value(true));
    }

    @Test void meDiceSiFaltaAceptarLosTerminos() throws Exception {
        when(auth.me(usuarioId)).thenReturn(usuario);
        when(terminos.pendiente(usuarioId)).thenReturn(true);
        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(jsonPath("$.datos.terminos_pendientes").value(true));
        when(terminos.pendiente(usuarioId)).thenReturn(false);
        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(jsonPath("$.datos.terminos_pendientes").value(false));
    }

    @Test void aceptarLosTerminosEsDelUsuarioDeLaSesion() throws Exception {
        when(terminos.aceptar(usuarioId)).thenReturn(new AceptacionDeTerminos(usuarioId, "2026-10-20", java.time.Instant.parse("2026-10-20T15:00:00Z")));
        mvc.perform(post("/v1/auth/terminos").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.version").value("2026-10-20"))
                .andExpect(jsonPath("$.datos.aceptada_en").value("2026-10-20T15:00:00Z"));
        verify(terminos).aceptar(usuarioId);
    }

    @Test void registroConDatosInvalidosEs422() throws Exception {
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"\",\"email\":\"\",\"password\":\"\",\"telefono\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void loginCorrecto() throws Exception {
        when(auth.login("ana@negocio.pe", "Segura123", null)).thenReturn(tokens);
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access").value("access-token"));
    }

    @Test void loginConCredencialesInvalidasEs401() throws Exception {
        when(auth.login(anyString(), anyString(), any())).thenThrow(new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@negocio.pe\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test void loginBloqueadoPorIntentosEs429() throws Exception {
        when(auth.login(anyString(), anyString(), any())).thenThrow(new DomainException("DEMASIADOS_INTENTOS_LOGIN", "Demasiados intentos fallidos"));
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS_LOGIN"));
    }

    @Test void refreshRota() throws Exception {
        when(auth.refrescar("refresh-viejo")).thenReturn(tokens);
        mvc.perform(post("/v1/auth/refresh").contentType("application/json").content("{\"refresh\":\"refresh-viejo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.refresh").value("refresh-token"));
    }

    @Test void logoutDevuelve204() throws Exception {
        mvc.perform(post("/v1/auth/logout").contentType("application/json").content("{\"refresh\":\"r\"}"))
                .andExpect(status().isNoContent());
        verify(auth).logout("r");
    }

    @Test void meDevuelveElUsuarioAutenticado() throws Exception {
        when(auth.me(usuarioId)).thenReturn(usuario);
        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("ana@negocio.pe"));
    }

    @Test void recuperarSiempreDevuelve202() throws Exception {
        mvc.perform(post("/v1/auth/recuperar").contentType("application/json").content("{\"email\":\"nadie@x.pe\"}"))
                .andExpect(status().isAccepted());
        verify(auth).solicitarRecuperacion("nadie@x.pe", "http://localhost:3000");
    }

    @Test void restablecerDevuelve204() throws Exception {
        mvc.perform(post("/v1/auth/restablecer").contentType("application/json").content("{\"token\":\"t\",\"password\":\"Nueva1234\"}"))
                .andExpect(status().isNoContent());
        verify(auth).restablecer("t", "Nueva1234");
    }

    // --- #22 ----------------------------------------------------------------------------------------------------------------------

    @Test void meDiceSiElCorreoEstaVerificado() throws Exception {
        when(auth.me(usuarioId)).thenReturn(usuario);
        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(jsonPath("$.datos.correo_verificado").value(false));
        when(auth.me(usuarioId)).thenReturn(usuario.conCorreoVerificado(java.time.Instant.parse("2026-10-03T15:00:00Z")));
        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(jsonPath("$.datos.correo_verificado").value(true));
    }

    @Test void verificarDevuelve204() throws Exception {
        mvc.perform(post("/v1/auth/verificar").contentType("application/json").content("{\"token\":\"t\"}"))
                .andExpect(status().isNoContent());
        verify(auth).verificarCorreo("t");
    }

    @Test void verificarSinTokenEs422() throws Exception {
        mvc.perform(post("/v1/auth/verificar").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void reenviarLaVerificacionEsDelUsuarioDeLaSesion() throws Exception {
        mvc.perform(post("/v1/auth/verificacion").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isAccepted());
        verify(auth).reenviarVerificacion(usuarioId, "http://localhost:3000");
    }

    @Test void reenviarConElCorreoYaVerificadoEs409() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("CORREO_YA_VERIFICADO", "ya")).when(auth).reenviarVerificacion(usuarioId, "http://localhost:3000");
        mvc.perform(post("/v1/auth/verificacion").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_YA_VERIFICADO"));
    }

    @Test void reenviarPasadoElTopeEs429() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("DEMASIADOS_ENLACES", "hoy no")).when(auth).reenviarVerificacion(usuarioId, "http://localhost:3000");
        mvc.perform(post("/v1/auth/verificacion").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_ENLACES"));
    }

    @Test void restablecerConTokenInvalidoEs422() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("TOKEN_INVALIDO", "Enlace vencido")).when(auth).restablecer("malo", "Nueva1234");
        mvc.perform(post("/v1/auth/restablecer").contentType("application/json").content("{\"token\":\"malo\",\"password\":\"Nueva1234\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    // --- #184: la sesión de soporte se dice en /me, para que el portal pueda avisar que se está actuando como ese cliente --------------------------

    @Test void meNoDiceNadaDeSoporteEnUnaSesionNormal() throws Exception {
        when(auth.me(usuarioId)).thenReturn(usuario);

        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.soporte_hasta").doesNotExist());
    }

    @Test void meDiceHastaCuandoVaLaSesionDeSoporte() throws Exception {
        when(auth.me(usuarioId)).thenReturn(usuario);

        mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId)
                        .requestAttr(SoporteActual.ATRIBUTO, new pe.factura.application.port.out.TokenEmisor.Soporte(UUID.randomUUID(), java.time.Instant.parse("2026-10-04T12:15:00Z"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.soporte_hasta").value("2026-10-04T12:15:00Z"))
                .andExpect(jsonPath("$.datos.email").value("ana@negocio.pe"));
    }

    /** El cliente nunca debe enterarse del id del administrador desde su propia sesión. */
    @Test void meNoDiceQueAdministradorEs() throws Exception {
        UUID admin = UUID.randomUUID();
        when(auth.me(usuarioId)).thenReturn(usuario);

        String cuerpo = mvc.perform(get("/v1/auth/me").requestAttr(UsuarioActual.ATRIBUTO, usuarioId)
                .requestAttr(SoporteActual.ATRIBUTO, new pe.factura.application.port.out.TokenEmisor.Soporte(admin, java.time.Instant.parse("2026-10-04T12:15:00Z")))).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain(admin.toString());
    }
}
