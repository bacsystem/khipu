package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AceptarInvitacionUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Rol;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #21: lo que hace el invitado con el enlace, sin sesión. */
@WebMvcTest(controllers = InvitacionController.class)
@Import(GlobalExceptionHandler.class)
class InvitacionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AceptarInvitacionUseCase invitaciones;

    @Test void muestraAQueLoInvitaron() throws Exception {
        when(invitaciones.consultar("tok")).thenReturn(new AceptarInvitacionUseCase.Detalle("caro@torres.pe", "Ferretería Torres", Rol.LECTURA, Instant.parse("2026-10-27T12:00:00Z")));

        mvc.perform(post("/v1/auth/invitacion").contentType("application/json").content("{\"token\":\"tok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("caro@torres.pe"))
                .andExpect(jsonPath("$.datos.cuenta").value("Ferretería Torres"))
                .andExpect(jsonPath("$.datos.rol").value("LECTURA"))
                .andExpect(jsonPath("$.datos.expira_en").value("2026-10-27T12:00:00Z"));
    }

    @Test void aceptarDevuelve204() throws Exception {
        mvc.perform(post("/v1/auth/invitacion/aceptar").contentType("application/json").content("{\"token\":\"tok\",\"password\":\"Segura123\"}"))
                .andExpect(status().isNoContent());
        verify(invitaciones).aceptar("tok", "Segura123");
    }

    @Test void unEnlaceInvalidoEs422() throws Exception {
        doThrow(new DomainException("TOKEN_INVALIDO", "venció")).when(invitaciones).aceptar("tok", "Segura123");

        mvc.perform(post("/v1/auth/invitacion/aceptar").contentType("application/json").content("{\"token\":\"tok\",\"password\":\"Segura123\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test void elCorreoConUsuarioEs409() throws Exception {
        doThrow(new DomainException("USUARIO_EXISTENTE", "ya tiene")).when(invitaciones).aceptar("tok", "Segura123");

        mvc.perform(post("/v1/auth/invitacion/aceptar").contentType("application/json").content("{\"token\":\"tok\",\"password\":\"Segura123\"}"))
                .andExpect(status().isConflict());
    }

    @Test void sinTokenNiContrasenaEs422SinLlegarAlServicio() throws Exception {
        mvc.perform(post("/v1/auth/invitacion/aceptar").contentType("application/json").content("{\"token\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores.token").exists())
                .andExpect(jsonPath("$.errores.password").exists());
        mvc.perform(post("/v1/auth/invitacion").contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(invitaciones);
    }
}
