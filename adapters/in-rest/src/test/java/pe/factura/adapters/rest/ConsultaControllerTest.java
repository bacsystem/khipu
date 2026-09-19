package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarValidezUseCase;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ConsultaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class ConsultaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ConsultarValidezUseCase validez;
    UUID tenant = UUID.randomUUID();

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
