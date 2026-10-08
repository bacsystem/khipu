package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarPlanesPublicadosUseCase;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los planes de la página de precios (H20): público, solo lo que se publica (nombre, precio, límites), sin ids ni cuentas. */
@WebMvcTest(controllers = PlanesPublicadosController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class PlanesPublicadosControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ConsultarPlanesPublicadosUseCase planes;

    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.sinLimite(), 5);

    @Test void listaNombrePrecioYLimitesDeCadaPlanPublicadoYNadaMas() throws Exception {
        when(planes.publicados()).thenReturn(List.of(new Plan(UUID.randomUUID(), "Emprende", new BigDecimal("29"), LIMITES, EstadoPlan.ACTIVO, false)));

        String json = mvc.perform(get("/v1/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].nombre").value("Emprende"))
                .andExpect(jsonPath("$.datos[0].precio_mensual").value(29.00))
                .andExpect(jsonPath("$.datos[0].limites.documentos_al_mes.maximo").value(300))
                .andExpect(jsonPath("$.datos[0].limites.api_keys.ilimitado").value(true))
                .andExpect(jsonPath("$.datos[0].length()").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("\"id\"").doesNotContain("cuentas").doesNotContain("visible");
    }

    @Test void sePuedeGuardarEnCacheUnosMinutos() throws Exception {
        when(planes.publicados()).thenReturn(List.of());

        mvc.perform(get("/v1/planes")).andExpect(header().string("Cache-Control", "max-age=300, public"));
    }

    @Test void esUnaRutaPublica() {
        assertThat(RutaRequest.esPublica("/v1/planes")).isTrue();
        assertThat(RutaRequest.esPublica("/v1/planes/otra")).isFalse();
    }
}
