package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.Administrador;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminAuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminAuthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AutenticarAdministradorUseCase auth;

    UUID administradorId = UUID.randomUUID();
    Administrador administrador = new Administrador(administradorId, "ana@khipu.pe", "hash", true);

    /** La contraseña ya no da un access token: da un desafío y dice qué paso sigue (#177). */
    @Test void loginDevuelveElDesafioYElPasoNoUnaSesion() throws Exception {
        when(auth.login("ana@khipu.pe", "Segura123")).thenReturn(new Desafio("desafio-1", Paso.VERIFICAR_SEGUNDO_FACTOR));
        mvc.perform(post("/v1/admin/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.desafio").value("desafio-1"))
                .andExpect(jsonPath("$.datos.paso").value("VERIFICAR_SEGUNDO_FACTOR"))
                .andExpect(jsonPath("$.datos.access_token").doesNotExist());
    }

    @Test void loginConCredencialesInvalidasEs401() throws Exception {
        when(auth.login(anyString(), anyString())).thenThrow(new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        mvc.perform(post("/v1/admin/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@khipu.pe\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test void loginConDatosInvalidosEs422() throws Exception {
        mvc.perform(post("/v1/admin/auth/login").contentType("application/json")
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void configurarDevuelveElSecretoLaUriYElQrEnBase64() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        when(auth.configurarSegundoFactor("desafio-1")).thenReturn(new Configuracion("SECRETO", "otpauth://totp/x", png));
        mvc.perform(post("/v1/admin/auth/segundo-factor/configurar").contentType("application/json").content("{\"desafio\":\"desafio-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.secreto").value("SECRETO"))
                .andExpect(jsonPath("$.datos.uri").value("otpauth://totp/x"))
                .andExpect(jsonPath("$.datos.qr_png").value(Base64.getEncoder().encodeToString(png)));
    }

    @Test void confirmarDevuelveLaSesionYLosCodigosDeRecuperacion() throws Exception {
        when(auth.confirmarSegundoFactor(eq("desafio-1"), eq("123456"), eq("203.0.113.7")))
                .thenReturn(new SesionNueva(new Sesion("access-token", 900, administrador), List.of("AAAAA-BBBBB", "CCCCC-DDDDD")));
        mvc.perform(post("/v1/admin/auth/segundo-factor/confirmar").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                        .contentType("application/json").content("{\"desafio\":\"desafio-1\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access_token").value("access-token"))
                .andExpect(jsonPath("$.datos.expira_en").value(900))
                .andExpect(jsonPath("$.datos.administrador.email").value("ana@khipu.pe"))
                .andExpect(jsonPath("$.datos.codigos_recuperacion[1]").value("CCCCC-DDDDD"));
    }

    @Test void verificarDevuelveLaSesionConLaIpDeLaConexion() throws Exception {
        when(auth.verificarSegundoFactor(eq("desafio-1"), eq("654321"), eq("198.51.100.4"))).thenReturn(new Sesion("access-token", 1800, administrador));
        mvc.perform(post("/v1/admin/auth/segundo-factor/verificar").with(r -> { r.setRemoteAddr("198.51.100.4"); return r; })
                        .contentType("application/json").content("{\"desafio\":\"desafio-1\",\"codigo\":\"654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access_token").value("access-token"))
                .andExpect(jsonPath("$.datos.expira_en").value(1800))
                .andExpect(jsonPath("$.datos.codigos_recuperacion").doesNotExist());
    }

    @Test void cadaFalloDelSegundoFactorTieneSuEstado() throws Exception {
        record Caso(String codigo, int estado) {}
        for (Caso c : List.of(new Caso("CODIGO_INVALIDO", 401), new Caso("SESION_INVALIDA", 401), new Caso("DEMASIADOS_INTENTOS", 429),
                new Caso("SEGUNDO_FACTOR_NO_CONFIGURADO", 409), new Caso("SEGUNDO_FACTOR_YA_CONFIGURADO", 409))) {
            doThrow(new DomainException(c.codigo(), "x")).when(auth).verificarSegundoFactor(anyString(), anyString(), any());
            mvc.perform(post("/v1/admin/auth/segundo-factor/verificar").contentType("application/json").content("{\"desafio\":\"d\",\"codigo\":\"1\"}"))
                    .andExpect(status().is(c.estado()))
                    .andExpect(jsonPath("$.codigo").value(c.codigo()));
        }
    }

    @Test void sinDesafioOSinCodigoEs422() throws Exception {
        mvc.perform(post("/v1/admin/auth/segundo-factor/configurar").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/admin/auth/segundo-factor/confirmar").contentType("application/json").content("{\"desafio\":\"d\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/admin/auth/segundo-factor/verificar").contentType("application/json").content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/admin/auth/segundo-factor/verificar").contentType("application/json")
                        .content("{\"desafio\":\"d\",\"codigo\":\"" + "1".repeat(65) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void meDevuelveElAdministradorAutenticado() throws Exception {
        when(auth.me(administradorId)).thenReturn(administrador);
        mvc.perform(get("/v1/admin/auth/me").requestAttr(AdministradorActual.ATRIBUTO, administradorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("ana@khipu.pe"));
    }
}
