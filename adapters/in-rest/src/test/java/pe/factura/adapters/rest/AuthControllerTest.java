package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AutenticarUsuarioUseCase auth;

    UUID usuarioId = UUID.randomUUID();
    UUID cuentaId = UUID.randomUUID();
    Usuario usuario = new Usuario(usuarioId, cuentaId, "ana@negocio.pe", "hash", Rol.ADMIN, true);
    AutenticarUsuarioUseCase.Tokens tokens = new AutenticarUsuarioUseCase.Tokens("access-token", "refresh-token", usuario);

    @Test void registroDevuelve201ConTokens() throws Exception {
        when(auth.registrar("Mi negocio", "ana@negocio.pe", "Segura123")).thenReturn(tokens);
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.access").value("access-token"))
                .andExpect(jsonPath("$.datos.refresh").value("refresh-token"))
                .andExpect(jsonPath("$.datos.usuario.email").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos.usuario.rol").value("ADMIN"));
    }

    @Test void registroConDatosInvalidosEs422() throws Exception {
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"\",\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void loginCorrecto() throws Exception {
        when(auth.login("ana@negocio.pe", "Segura123")).thenReturn(tokens);
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access").value("access-token"));
    }

    @Test void loginConCredencialesInvalidasEs401() throws Exception {
        when(auth.login(anyString(), anyString())).thenThrow(new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@negocio.pe\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
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

    @Test void restablecerConTokenInvalidoEs422() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException("TOKEN_INVALIDO", "Enlace vencido")).when(auth).restablecer("malo", "Nueva1234");
        mvc.perform(post("/v1/auth/restablecer").contentType("application/json").content("{\"token\":\"malo\",\"password\":\"Nueva1234\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }
}
