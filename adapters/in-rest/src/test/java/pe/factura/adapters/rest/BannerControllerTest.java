package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarBannerUseCase;
import pe.factura.domain.plataforma.BannerDeMantenimiento;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El aviso de mantenimiento que ve todo cliente (#199): solo el texto y la vigencia, sin guardarse en caché, y nulo cuando no hay nada que mostrar. */
@WebMvcTest(controllers = BannerController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class BannerControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ConsultarBannerUseCase banner;

    @Test void conUnAvisoVigenteDiceSuTextoYSuVigenciaYNadaMas() throws Exception {
        when(banner.vigente()).thenReturn(Optional.of(new BannerDeMantenimiento("Mantenimiento esta noche", Instant.parse("2026-10-15T20:00:00Z"), Instant.parse("2026-10-16T01:00:00Z"))));

        mvc.perform(get("/v1/banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.texto").value("Mantenimiento esta noche"))
                .andExpect(jsonPath("$.datos.desde").value("2026-10-15T20:00:00Z"))
                .andExpect(jsonPath("$.datos.hasta").value("2026-10-16T01:00:00Z"))
                .andExpect(jsonPath("$.datos.length()").value(3));
    }

    @Test void sinAvisoVigenteLosDatosSonNulos() throws Exception {
        when(banner.vigente()).thenReturn(Optional.empty());

        mvc.perform(get("/v1/banner")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("exito")).andExpect(jsonPath("$.datos").doesNotExist());
    }

    /** Un aviso que se retira o que vence tiene que dejar de verse ya: ni el navegador ni un intermediario lo guardan. */
    @Test void nuncaSeGuardaEnCache() throws Exception {
        when(banner.vigente()).thenReturn(Optional.empty());

        mvc.perform(get("/v1/banner")).andExpect(header().string("Cache-Control", "no-store"));
    }
}
