package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
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

    @Test void loginCorrectoDevuelveToken() throws Exception {
        when(auth.login("ana@khipu.pe", "Segura123")).thenReturn(new AutenticarAdministradorUseCase.Sesion("access-token", administrador));
        mvc.perform(post("/v1/admin/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.access_token").value("access-token"))
                .andExpect(jsonPath("$.datos.administrador.email").value("ana@khipu.pe"));
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

    @Test void meDevuelveElAdministradorAutenticado() throws Exception {
        when(auth.me(administradorId)).thenReturn(administrador);
        mvc.perform(get("/v1/admin/auth/me").requestAttr(AdministradorActual.ATRIBUTO, administradorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("ana@khipu.pe"));
    }
}
