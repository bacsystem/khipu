package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ResumirComprobantesUseCase;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Resumen;
import pe.factura.application.port.in.ResumirComprobantesUseCase.Total;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El resumen de comprobantes de la empresa (#15). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su forma real (snake_case, el desglose
 * de la atención requerida, las monedas) y cómo se rechaza un rango mal pedido.
 */
@WebMvcTest(controllers = FacturaResumenController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class FacturaResumenControllerTest {
    static final UUID EMPRESA = UUID.randomUUID();
    static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    @Autowired MockMvc mvc;
    @MockBean ResumirComprobantesUseCase resumir;

    @Test void elResumenDiceLoEmitidoLoAceptadoLaAtencionDesglosadaYLoFacturadoPorMoneda() throws Exception {
        when(resumir.resumir(EMPRESA, DESDE, HASTA)).thenReturn(new Resumen(DESDE, HASTA, 120, 100, 3, 2, 1, List.of(new Total("PEN", new BigDecimal("12345.67")), new Total("USD", new BigDecimal("500.00")))));

        mvc.perform(get("/v1/facturas/resumen").param("desde", "2026-09-01").param("hasta", "2026-09-30").requestAttr(TenantActual.ATRIBUTO, EMPRESA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.datos.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.datos.emitidos").value(120))
                .andExpect(jsonPath("$.datos.aceptados_con_cdr").value(100))
                .andExpect(jsonPath("$.datos.atencion_requerida.total").value(6))
                .andExpect(jsonPath("$.datos.atencion_requerida.rechazados").value(3))
                .andExpect(jsonPath("$.datos.atencion_requerida.errores_de_envio").value(2))
                .andExpect(jsonPath("$.datos.atencion_requerida.fuera_de_plazo").value(1))
                .andExpect(jsonPath("$.datos.facturado.length()").value(2))
                .andExpect(jsonPath("$.datos.facturado[0].moneda").value("PEN"))
                .andExpect(jsonPath("$.datos.facturado[0].total").value(12345.67))
                .andExpect(jsonPath("$.datos.facturado[1].moneda").value("USD"))
                .andExpect(jsonPath("$.datos.facturado[1].total").value(500.0));
    }

    @Test void sinFacturadoLaListaVaVaciaYNoAusente() throws Exception {
        when(resumir.resumir(EMPRESA, null, null)).thenReturn(new Resumen(null, null, 0, 0, 0, 0, 0, List.of()));

        mvc.perform(get("/v1/facturas/resumen").requestAttr(TenantActual.ATRIBUTO, EMPRESA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.emitidos").value(0))
                .andExpect(jsonPath("$.datos.atencion_requerida.total").value(0))
                .andExpect(jsonPath("$.datos.facturado.length()").value(0));
    }

    @Test void unRangoAbiertoNoMandaFechasNiLasDevuelve() throws Exception {
        when(resumir.resumir(EMPRESA, null, HASTA)).thenReturn(new Resumen(null, HASTA, 1, 1, 0, 0, 0, List.of()));

        mvc.perform(get("/v1/facturas/resumen").param("hasta", "2026-09-30").requestAttr(TenantActual.ATRIBUTO, EMPRESA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.desde").doesNotExist())
                .andExpect(jsonPath("$.datos.hasta").value("2026-09-30"));

        verify(resumir).resumir(EMPRESA, null, HASTA);
    }

    @Test void pasaAlCasoDeUsoLaEmpresaAutenticadaYLasFechasTalCual() throws Exception {
        when(resumir.resumir(any(), any(), any())).thenReturn(new Resumen(DESDE, HASTA, 0, 0, 0, 0, 0, List.of()));

        mvc.perform(get("/v1/facturas/resumen").param("desde", "2026-09-01").param("hasta", "2026-09-30").requestAttr(TenantActual.ATRIBUTO, EMPRESA)).andExpect(status().isOk());

        verify(resumir).resumir(EMPRESA, DESDE, HASTA);
    }

    @Test void unRangoAlReversEs400ConSuCodigo() throws Exception {
        when(resumir.resumir(any(), any(), any())).thenThrow(new DomainException("RANGO_INVALIDO", "desde no puede ser posterior a hasta"));

        mvc.perform(get("/v1/facturas/resumen").param("desde", "2026-09-30").param("hasta", "2026-09-01").requestAttr(TenantActual.ATRIBUTO, EMPRESA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));
    }

    @Test void unaFechaMalEscritaEs400SinLlamarAlCasoDeUso() throws Exception {
        for (String mala : List.of("01/09/2026", "2026-02-30", "ayer", "2026-9-1")) {
            mvc.perform(get("/v1/facturas/resumen").param("desde", mala).requestAttr(TenantActual.ATRIBUTO, EMPRESA)).andExpect(status().isBadRequest());
            mvc.perform(get("/v1/facturas/resumen").param("hasta", mala).requestAttr(TenantActual.ATRIBUTO, EMPRESA)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(resumir);
    }

    @Test void sinEmpresaAutenticadaPideUnaEmpresa() throws Exception {
        mvc.perform(get("/v1/facturas/resumen")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("EMPRESA_REQUERIDA"));

        verifyNoInteractions(resumir);
    }
}
