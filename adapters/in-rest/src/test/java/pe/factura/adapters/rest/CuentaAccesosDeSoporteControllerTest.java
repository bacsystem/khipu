package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AccesosDeSoporteUseCase;
import pe.factura.application.port.in.AccesosDeSoporteUseCase.AccesoDeSoporte;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El historial de accesos de soporte que ve el propio cliente (#184). */
@WebMvcTest(controllers = CuentaAccesosDeSoporteController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class CuentaAccesosDeSoporteControllerTest {
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockBean AccesosDeSoporteUseCase accesos;

    @Test void devuelveLosAccesosDeLaCuentaDeLaSesion() throws Exception {
        when(accesos.deLaCuenta(CUENTA)).thenReturn(List.of(new AccesoDeSoporte(Instant.parse("2026-10-04T10:00:00Z"), "ana@negocio.pe", 900L)));

        mvc.perform(get("/v1/cuenta/accesos-de-soporte").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].ocurrido_en").value("2026-10-04T10:00:00Z"))
                .andExpect(jsonPath("$.datos[0].usuario").value("ana@negocio.pe"))
                .andExpect(jsonPath("$.datos[0].duracion_segundos").value(900));
        verify(accesos).deLaCuenta(CUENTA);
    }

    /** El cliente no sabe qué administrador fue: ni su id ni su correo salen en la respuesta. */
    @Test void nadaDelAdministradorSaleEnLaRespuesta() throws Exception {
        when(accesos.deLaCuenta(CUENTA)).thenReturn(List.of(new AccesoDeSoporte(Instant.parse("2026-10-04T10:00:00Z"), "ana@negocio.pe", 900L)));

        mvc.perform(get("/v1/cuenta/accesos-de-soporte").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(jsonPath("$.datos[0].administrador").doesNotExist())
                .andExpect(jsonPath("$.datos[0].administrador_id").doesNotExist())
                .andExpect(jsonPath("$.datos[0].actor").doesNotExist());
    }

    @Test void unRegistroQueNoSeEntiendeSaleSoloConLaFecha() throws Exception {
        when(accesos.deLaCuenta(CUENTA)).thenReturn(List.of(new AccesoDeSoporte(Instant.parse("2026-10-04T10:00:00Z"), null, null)));

        mvc.perform(get("/v1/cuenta/accesos-de-soporte").requestAttr(CuentaActual.ATRIBUTO, CUENTA))
                .andExpect(jsonPath("$.datos[0].ocurrido_en").value("2026-10-04T10:00:00Z"))
                .andExpect(jsonPath("$.datos[0].usuario").doesNotExist())
                .andExpect(jsonPath("$.datos[0].duracion_segundos").doesNotExist());
    }

    @Test void sinAccesosDevuelveUnaListaVacia() throws Exception {
        when(accesos.deLaCuenta(CUENTA)).thenReturn(List.of());

        mvc.perform(get("/v1/cuenta/accesos-de-soporte").requestAttr(CuentaActual.ATRIBUTO, CUENTA)).andExpect(status().isOk()).andExpect(jsonPath("$.datos").isEmpty());
    }

    /** Es del portal: con una API key no hay cuenta (solo empresa), y el historial es de la cuenta. */
    @Test void sinSesionDeCuentaEsNoAutorizado() throws Exception {
        mvc.perform(get("/v1/cuenta/accesos-de-soporte")).andExpect(status().isUnauthorized());

        verifyNoInteractions(accesos);
    }
}
