package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.CrearAdministradorUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdministradorController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdministradorControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CrearAdministradorUseCase crear;

    @Test void creaDevuelve201() throws Exception {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", "hash", true);
        when(crear.crear("ana@khipu.pe", "Segura123")).thenReturn(a);
        mvc.perform(post("/v1/admin/administradores").contentType("application/json")
                        .content("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.email").value("ana@khipu.pe"));
    }

    @Test void duplicadoEs409() throws Exception {
        when(crear.crear("ana@khipu.pe", "Segura123")).thenThrow(new DomainException("DUPLICADO", "Ya existe un administrador con ese correo"));
        mvc.perform(post("/v1/admin/administradores").contentType("application/json")
                        .content("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test void datosInvalidosEs422() throws Exception {
        mvc.perform(post("/v1/admin/administradores").contentType("application/json")
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
