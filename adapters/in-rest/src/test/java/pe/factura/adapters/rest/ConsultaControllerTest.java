package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarPadronRucUseCase;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ConsultaController.class)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class ConsultaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConsultarValidezUseCase validez;
    @MockitoBean ConsultarPadronRucUseCase padron;
    UUID tenant = UUID.randomUUID();

    @Test void consultaUnRucDelPadron() throws Exception {
        when(padron.consultar(tenant, "20100066603")).thenReturn(new ConsultarPadronRucUseCase.Consulta(
                new PadronRucRepository.Contribuyente("20100066603", "TIENDAS S.A.C.", "ACTIVO", "HABIDO", "150101", "AV. PERU NRO. 1"), Instant.parse("2026-10-15T09:40:00Z")));
        mvc.perform(get("/v1/consultas/ruc/20100066603").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.razon_social").value("TIENDAS S.A.C."))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.condicion").value("HABIDO"))
                .andExpect(jsonPath("$.datos.activo").value(true))
                .andExpect(jsonPath("$.datos.habido").value(true))
                .andExpect(jsonPath("$.datos.ubigeo").value("150101"))
                .andExpect(jsonPath("$.datos.direccion").value("AV. PERU NRO. 1"))
                .andExpect(jsonPath("$.datos.actualizado_al").value("2026-10-15T09:40:00Z"));
    }

    @Test void unRucDeBajaNoEsActivoNiHabido() throws Exception {
        when(padron.consultar(tenant, "20100066603")).thenReturn(new ConsultarPadronRucUseCase.Consulta(
                new PadronRucRepository.Contribuyente("20100066603", "TIENDAS S.A.C.", "BAJA DE OFICIO", "NO HABIDO", null, null), Instant.parse("2026-10-15T09:40:00Z")));
        mvc.perform(get("/v1/consultas/ruc/20100066603").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.activo").value(false))
                .andExpect(jsonPath("$.datos.habido").value(false))
                .andExpect(jsonPath("$.datos.direccion").doesNotExist());
    }

    @Test void loQueElPadronNoPuedeDarSeTraduceAlCodigoHttpQueCorresponde() throws Exception {
        record Caso(String codigo, int http) { }
        for (Caso c : new Caso[]{new Caso("NO_ENCONTRADO", 404), new Caso("PARAMETRO_INVALIDO", 400), new Caso("PADRON_NO_CARGADO", 503), new Caso("DEMASIADAS_CONSULTAS", 429)}) {
            doThrow(new DomainException(c.codigo(), "x")).when(padron).consultar(any(), any());
            mvc.perform(get("/v1/consultas/ruc/20100066603").requestAttr(TenantActual.ATRIBUTO, tenant))
                    .andExpect(status().is(c.http())).andExpect(jsonPath("$.codigo").value(c.codigo()));
        }
    }

    /** #318: por la API REST la respuesta trae la fuente, el estado del RUC, la condición de domicilio y las observaciones; por SOAP, sin ellos. */
    @Test void laValidezPorLaApiRestTraeLoQueSoloElla() throws Exception {
        when(validez.consultar(eq(tenant), any())).thenReturn(new ConsultarValidezUseCase.Validez("ACEPTADO", "1", "Aceptado", ConsultarValidezUseCase.Fuente.API_REST,
                "00", "12", java.util.List.of("- Fecha de emisión ingresada no coincide con la registrada en la SUNAT."), null));
        mvc.perform(get("/v1/consultas/validez").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .param("ruc", "20100066603").param("tipo", "01").param("serie", "F001").param("numero", "12").param("fecha", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.fuente").value("API_REST"))
                .andExpect(jsonPath("$.datos.codigo").value("1"))
                .andExpect(jsonPath("$.datos.estado_ruc").value("00"))
                .andExpect(jsonPath("$.datos.condicion_domicilio").value("12"))
                .andExpect(jsonPath("$.datos.observaciones[0]").value(org.hamcrest.Matchers.startsWith("- Fecha")))
                .andExpect(jsonPath("$.datos.aviso").doesNotExist());

        when(validez.consultar(eq(tenant), any())).thenReturn(new ConsultarValidezUseCase.Validez("ACEPTADO", "0001", "ok", ConsultarValidezUseCase.Fuente.SOAP,
                null, null, java.util.List.of(), "La API de SUNAT no respondió (HTTP 503): se usó billValidService con la clave SOL"));
        mvc.perform(get("/v1/consultas/validez").requestAttr(TenantActual.ATRIBUTO, tenant).param("ruc", "20100066603").param("tipo", "01").param("serie", "F001").param("numero", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.fuente").value("SOAP"))
                .andExpect(jsonPath("$.datos.aviso").value(org.hamcrest.Matchers.containsString("503")))
                .andExpect(jsonPath("$.datos.observaciones").doesNotExist())
                .andExpect(jsonPath("$.datos.estado_ruc").doesNotExist());
    }

    @Test void losErroresDeLaValidezTienenSuCodigoHttp() throws Exception {
        record Caso(String codigo, int http) { }
        for (Caso c : new Caso[]{new Caso("SUNAT_NO_DISPONIBLE", 503), new Caso("CREDENCIALES_SUNAT_NO_CARGADAS", 422), new Caso("CREDENCIALES_API_SUNAT_RECHAZADAS", 422),
                new Caso("CONSULTA_RECHAZADA", 422), new Caso("PARAMETRO_INVALIDO", 400)}) {
            doThrow(new DomainException(c.codigo(), "x")).when(validez).consultar(any(), any());
            mvc.perform(get("/v1/consultas/validez").requestAttr(TenantActual.ATRIBUTO, tenant).param("ruc", "20100066603").param("tipo", "01").param("serie", "F001").param("numero", "12"))
                    .andExpect(status().is(c.http())).andExpect(jsonPath("$.codigo").value(c.codigo()));
        }
    }

    @Test void consultaValidezConCriterios() throws Exception {
        when(validez.consultar(eq(tenant), any())).thenReturn(new ConsultarValidezUseCase.Validez("ACEPTADO", "0001", "El comprobante existe y está aceptado."));
        mvc.perform(get("/v1/consultas/validez").requestAttr(TenantActual.ATRIBUTO, tenant)
                        .param("ruc", "20100066603").param("tipo", "01").param("serie", "F001").param("numero", "12")
                        .param("tipo_doc_receptor", "6").param("num_doc_receptor", "20601234565").param("fecha", "2026-09-10").param("monto", "118.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.codigo").value("0001"));
        verify(validez).consultar(eq(tenant), argThat(q -> q.rucEmisor().equals("20100066603") && q.numero() == 12 && q.numDocReceptor().equals("20601234565")
                && q.fechaEmision().equals(LocalDate.of(2026, 9, 10)) && q.importeTotal().compareTo(new BigDecimal("118.00")) == 0));

        when(validez.consultar(eq(tenant), any())).thenThrow(new DomainException("NO_DISPONIBLE_EN_BETA", "solo producción"));
        mvc.perform(get("/v1/consultas/validez").requestAttr(TenantActual.ATRIBUTO, tenant).param("ruc", "20100066603").param("tipo", "01").param("serie", "F001").param("numero", "12"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NO_DISPONIBLE_EN_BETA"));
    }
}
