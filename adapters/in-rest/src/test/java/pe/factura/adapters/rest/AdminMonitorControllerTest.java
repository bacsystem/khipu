package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.MonitorearEmisionUseCase;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Franja;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Monitor;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Outbox;
import pe.factura.application.port.out.SondeoDeSunat.Resultado;
import pe.factura.application.port.out.SondeoDeSunat.Servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El monitor global de emisión (#195). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su **forma real** (snake_case, los opcionales
 * ausentes en lugar de nulos, la duración en segundos) para que la pantalla y el endpoint no se desencuentren.
 */
@WebMvcTest(controllers = AdminMonitorController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminMonitorControllerTest {
    static final Instant AHORA = Instant.parse("2026-10-15T15:20:00Z");
    static final Instant HORA = Instant.parse("2026-10-15T15:00:00Z");

    @Autowired MockMvc mvc;
    @MockBean MonitorearEmisionUseCase monitor;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static Monitor monitor(Outbox outbox, List<Resultado> sunat) {
        List<Franja> horas = new ArrayList<>();
        for (int i = 0; i < 24; i++) horas.add(new Franja(HORA.minus(Duration.ofHours(23 - i)), 0, 0, 0, 0, 0));
        horas.set(23, new Franja(HORA, 97, 3, 2, 10, 5));
        return new Monitor(AHORA, horas, new Franja(Instant.parse("2026-10-15T05:00:00Z"), 400, 12, 4, 10, 9), outbox, sunat);
    }

    @Test void laLecturaTraeLasHorasElDiaLaColaYSunatConSusNombresEnSnakeCase() throws Exception {
        when(monitor.monitorear()).thenReturn(monitor(new Outbox(12, 2, Instant.parse("2026-10-15T12:00:00Z"), Duration.ofSeconds(420), true),
                List.of(new Resultado(Servicio.ENVIO_PRODUCCION, true, 140L, null), new Resultado(Servicio.CONSULTA_DE_CDR, false, null, "HTTP 503"))));

        mvc.perform(get("/v1/admin/monitor").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.generado_en").value("2026-10-15T15:20:00Z"))
                .andExpect(jsonPath("$.datos.horas.length()").value(24))
                .andExpect(jsonPath("$.datos.horas[0].desde").value("2026-10-14T16:00:00Z"))
                .andExpect(jsonPath("$.datos.horas[23].desde").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos.horas[23].total").value(117))
                .andExpect(jsonPath("$.datos.horas[23].aceptados").value(97))
                .andExpect(jsonPath("$.datos.horas[23].rechazados").value(3))
                .andExpect(jsonPath("$.datos.horas[23].con_error").value(2))
                .andExpect(jsonPath("$.datos.horas[23].en_camino").value(10))
                .andExpect(jsonPath("$.datos.horas[23].otros").value(5))
                .andExpect(jsonPath("$.datos.horas[23].tasa_de_rechazo").value(0.03))
                .andExpect(jsonPath("$.datos.hoy.desde").value("2026-10-15T05:00:00Z"))
                .andExpect(jsonPath("$.datos.hoy.total").value(435))
                .andExpect(jsonPath("$.datos.outbox.pendientes").value(12))
                .andExpect(jsonPath("$.datos.outbox.vencidos").value(2))
                .andExpect(jsonPath("$.datos.outbox.mas_viejo_desde").value("2026-10-15T12:00:00Z"))
                .andExpect(jsonPath("$.datos.outbox.vencido_hace_segundos").value(420))
                .andExpect(jsonPath("$.datos.outbox.alerta").value(true))
                .andExpect(jsonPath("$.datos.sunat.length()").value(2))
                .andExpect(jsonPath("$.datos.sunat[0].servicio").value("ENVIO_PRODUCCION"))
                .andExpect(jsonPath("$.datos.sunat[0].disponible").value(true))
                .andExpect(jsonPath("$.datos.sunat[0].milisegundos").value(140))
                .andExpect(jsonPath("$.datos.sunat[1].servicio").value("CONSULTA_DE_CDR"))
                .andExpect(jsonPath("$.datos.sunat[1].disponible").value(false))
                .andExpect(jsonPath("$.datos.sunat[1].detalle").value("HTTP 503"));
    }

    /** El portal distingue «no hay dato» de «cero»: lo que no existe no viaja como nulo. */
    @Test void loOpcionalVaAusenteEnLugarDeNulo() throws Exception {
        when(monitor.monitorear()).thenReturn(monitor(new Outbox(0, 0, null, null, false), List.of(new Resultado(Servicio.ENVIO_BETA, true, 90L, null))));

        mvc.perform(get("/v1/admin/monitor").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.horas[0].tasa_de_rechazo").doesNotExist())
                .andExpect(jsonPath("$.datos.outbox.mas_viejo_desde").doesNotExist())
                .andExpect(jsonPath("$.datos.outbox.vencido_hace_segundos").doesNotExist())
                .andExpect(jsonPath("$.datos.outbox.alerta").value(false))
                .andExpect(jsonPath("$.datos.sunat[0].detalle").doesNotExist());
    }

    @Test void unaFranjaSinComprobantesTrae_ceroYNoFalta() throws Exception {
        when(monitor.monitorear()).thenReturn(monitor(new Outbox(0, 0, null, null, false), List.of()));

        mvc.perform(get("/v1/admin/monitor").with(clave()))
                .andExpect(jsonPath("$.datos.horas[0].total").value(0))
                .andExpect(jsonPath("$.datos.horas[0].aceptados").value(0))
                .andExpect(jsonPath("$.datos.sunat.length()").value(0));
    }

    @Test void noTraeMasCamposQueLosQueElPortalConoce() throws Exception {
        when(monitor.monitorear()).thenReturn(monitor(new Outbox(1, 1, AHORA, Duration.ofSeconds(1), false), List.of()));

        mvc.perform(get("/v1/admin/monitor").with(clave()))
                .andExpect(jsonPath("$.datos.generadoEn").doesNotExist())
                .andExpect(jsonPath("$.datos.horas[23].conError").doesNotExist())
                .andExpect(jsonPath("$.datos.horas[23].enCamino").doesNotExist())
                .andExpect(jsonPath("$.datos.horas[23].tasaDeRechazo").doesNotExist())
                .andExpect(jsonPath("$.datos.outbox.vencidoHace").doesNotExist())
                .andExpect(jsonPath("$.datos.outbox.vencidoHaceSegundos").doesNotExist());
    }
}
