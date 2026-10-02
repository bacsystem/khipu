package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.CrearAdministradorUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdministradorController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdministradorControllerTest {
    static final String CUERPO = "{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}";
    static final ActorAdmin CLAVE = ActorAdmin.clavePlataforma("127.0.0.1");   // IP por defecto de MockMvc

    @Autowired MockMvc mvc;
    @MockBean CrearAdministradorUseCase crear;

    @Test void creaDevuelve201() throws Exception {
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", "hash", true);
        when(crear.crear(CLAVE, "ana@khipu.pe", "Segura123")).thenReturn(a);
        mvc.perform(post("/v1/admin/administradores").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.email").value("ana@khipu.pe"));
    }

    @Test void creaAtribuidoAlAdministradorQueTieneSesionDesdeSuIp() throws Exception {
        UUID quien = UUID.randomUUID();
        ActorAdmin actor = ActorAdmin.administrador(quien, "203.0.113.7");
        Administrador a = new Administrador(UUID.randomUUID(), "ana@khipu.pe", "hash", true);
        when(crear.crear(actor, "ana@khipu.pe", "Segura123")).thenReturn(a);
        mvc.perform(post("/v1/admin/administradores").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO, quien).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isCreated());
        verify(crear).crear(actor, "ana@khipu.pe", "Segura123");
    }

    @Test void sinCredencialNoCreaNadaYEs401() throws Exception {
        mvc.perform(post("/v1/admin/administradores").contentType("application/json").content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
        verify(crear, never()).crear(any(), any(), any());
    }

    @Test void duplicadoEs409() throws Exception {
        when(crear.crear(CLAVE, "ana@khipu.pe", "Segura123")).thenThrow(new DomainException("DUPLICADO", "Ya existe un administrador con ese correo"));
        mvc.perform(post("/v1/admin/administradores").contentType("application/json").content(CUERPO)
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test void datosInvalidosEs422() throws Exception {
        mvc.perform(post("/v1/admin/administradores").contentType("application/json")
                        .content("{\"email\":\"\",\"password\":\"\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isUnprocessableEntity());
    }
}
