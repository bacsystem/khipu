package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase.EstadoDeBaja;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dar de baja y reponer una cuenta desde el backoffice (#201). */
@WebMvcTest(controllers = AdminBajaCuentaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminBajaCuentaControllerTest {
    static final UUID ID = UUID.randomUUID();
    static final Instant BAJA = Instant.parse("2026-10-03T09:00:00Z");

    @Autowired MockMvc mvc;
    @MockBean DarDeBajaCuentaUseCase baja;

    @Test void darDeBajaPasaElActorYElMotivoYDiceDesdeCuando() throws Exception {
        UUID admin = UUID.randomUUID();
        var actor = ActorAdmin.administrador(admin, "203.0.113.7");
        when(baja.darDeBaja(actor, ID, "cerró su negocio")).thenReturn(new EstadoDeBaja(ID, BAJA));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja").contentType("application/json").content("{\"motivo\":\"cerró su negocio\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO, admin).with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void darDeBajaSinCuerpoEsValidoYNoLlevaMotivo() throws Exception {
        when(baja.darDeBaja(ActorAdmin.clavePlataforma("127.0.0.1"), ID, null)).thenReturn(new EstadoDeBaja(ID, BAJA));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.baja_en").value("2026-10-03T09:00:00Z"));
    }

    @Test void reponerDevuelveLaCuentaSinFechaDeBaja() throws Exception {
        when(baja.reponer(ActorAdmin.clavePlataforma("127.0.0.1"), ID)).thenReturn(new EstadoDeBaja(ID, null));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reponer").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").value(ID.toString()))
                .andExpect(jsonPath("$.datos.baja_en").doesNotExist());
    }

    @Test void darDeBajaUnaCuentaYaDeBajaEsConflicto() throws Exception {
        when(baja.darDeBaja(any(), eq(ID), any())).thenThrow(new DomainException("CUENTA_YA_DE_BAJA", "La cuenta ya está dada de baja"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_YA_DE_BAJA"));
    }

    @Test void reponerUnaCuentaQueNoEstaDeBajaEsConflicto() throws Exception {
        when(baja.reponer(any(), eq(ID))).thenThrow(new DomainException("CUENTA_NO_DE_BAJA", "La cuenta no está dada de baja"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reponer").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_DE_BAJA"));
    }

    @Test void unMotivoDemasiadoLargoEsUnDatoInvalido() throws Exception {
        when(baja.darDeBaja(any(), eq(ID), any())).thenThrow(new DomainException("MOTIVO_INVALIDO", "El motivo no puede pasar de 200 caracteres"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja").contentType("application/json").content("{\"motivo\":\"x\"}")
                        .requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
    }

    @Test void unaCuentaQueNoExisteEsNoEncontrada() throws Exception {
        when(baja.darDeBaja(any(), eq(ID), any())).thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));
        when(baja.reponer(any(), eq(ID))).thenThrow(new DomainException("NO_ENCONTRADO", "La cuenta no existe"));

        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isNotFound());
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reponer").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isNotFound());
    }

    @Test void sinAdministradorNiClaveAutenticadosLaAccionNoSeEjecuta() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/baja")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/admin/cuentas/" + ID + "/reponer")).andExpect(status().isUnauthorized());

        verifyNoInteractions(baja);
    }

    @Test void unIdentificadorQueNoEsUnUuidEs400SinHacerNada() throws Exception {
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/baja").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/cuentas/no-es-un-uuid/reponer").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE)).andExpect(status().isBadRequest());

        verifyNoInteractions(baja);
    }
}
