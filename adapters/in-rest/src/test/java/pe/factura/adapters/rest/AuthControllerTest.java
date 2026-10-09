package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code app.registro-abierto} se fija explícito: el default real (issue #174) es cerrado, ver {@link AuthControllerRegistroCerradoTest}. */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, IpDelCliente.class})
@TestPropertySource(properties = "app.registro-abierto=true")
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AutenticarUsuarioUseCase auth;

    UUID usuarioId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    Usuario usuario = new Usuario(usuarioId, cuentaId, "ana@negocio.pe", "hash", Rol.ADMIN, true);
    AutenticarUsuarioUseCase.Tokens tokens = new AutenticarUsuarioUseCase.Tokens("access-token", "refresh-token", usuario);

    @Test void registroDevuelve201ConTokens() throws Exception {
        when(auth.registrar("Mi negocio", "ana@negocio.pe", "Segura123", "987654321", "http://localhost:3000")).thenReturn(tokens);
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.access").value("access-token"))
                .andExpect(jsonPath("$.datos.refresh").value("refresh-token"))
                .andExpect(jsonPath("$.datos.usuario.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.usuario.rol").value("ADMIN"));
        verify(auth).registrar("Mi negocio", "ana@negocio.pe", "Segura123", "987654321", "http://localhost:3000");
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
