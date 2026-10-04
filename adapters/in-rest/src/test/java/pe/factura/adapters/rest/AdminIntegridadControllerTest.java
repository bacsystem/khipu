package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.VerificarIntegridadUseCase;
import pe.factura.application.port.in.VerificarIntegridadUseCase.Informe;
import pe.factura.application.port.in.VerificarIntegridadUseCase.Problema;
import pe.factura.domain.DomainException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La verificación de integridad del storage desde el backoffice (#198). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su **forma real**
 * (nombres en snake_case, sin campos de más) y cómo rechaza un rango mal pedido, para que la pantalla y el endpoint no se desencuentren.
 */
@WebMvcTest(controllers = AdminIntegridadController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminIntegridadControllerTest {
    static final UUID COMPROBANTE = UUID.randomUUID();
    static final UUID EMPRESA = UUID.randomUUID();
    static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    @Autowired MockMvc mvc;
    @MockBean VerificarIntegridadUseCase integridad;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    @Test void elInformeDiceElRangoCuantosSeVerificaronYCadaProblemaConSuComprobanteYSuEmpresa() throws Exception {
        when(integridad.verificar(DESDE, HASTA)).thenReturn(new Informe(DESDE, HASTA, 120, List.of(
                new Problema(COMPROBANTE, EMPRESA, "20100066603-01-F001-123", "XML_CORRUPTO", "el DigestValue registrado no está en k/20100066603-01-F001-123.xml"),
                new Problema(UUID.randomUUID(), EMPRESA, "20100066603-03-B001-9", "CDR_FALTANTE", "k/R-20100066603-03-B001-9.zip"))));

        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01").param("hasta", "2026-09-30").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.desde").value("2026-09-01"))
                .andExpect(jsonPath("$.datos.hasta").value("2026-09-30"))
                .andExpect(jsonPath("$.datos.verificados").value(120))
                .andExpect(jsonPath("$.datos.problemas.length()").value(2))
                .andExpect(jsonPath("$.datos.problemas[0].comprobante_id").value(COMPROBANTE.toString()))
                .andExpect(jsonPath("$.datos.problemas[0].tenant_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos.problemas[0].nombre_archivo").value("20100066603-01-F001-123"))
                .andExpect(jsonPath("$.datos.problemas[0].tipo").value("XML_CORRUPTO"))
                .andExpect(jsonPath("$.datos.problemas[0].detalle").value("el DigestValue registrado no está en k/20100066603-01-F001-123.xml"))
                .andExpect(jsonPath("$.datos.problemas[1].tipo").value("CDR_FALTANTE"));
    }

    /** Un barrido sin problemas es una lista vacía, no un campo ausente: el portal distingue «limpio» de «no llegó». */
    @Test void unBarridoLimpioTraeLaListaDeProblemasVacia() throws Exception {
        when(integridad.verificar(DESDE, HASTA)).thenReturn(new Informe(DESDE, HASTA, 8, List.of()));

        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01").param("hasta", "2026-09-30").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.verificados").value(8))
                .andExpect(jsonPath("$.datos.problemas.length()").value(0));
    }

    @Test void elInformeNoTraeMasCamposQueLosQueElPortalConoce() throws Exception {
        when(integridad.verificar(DESDE, HASTA)).thenReturn(new Informe(DESDE, HASTA, 1, List.of(new Problema(COMPROBANTE, EMPRESA, "a", "XML_FALTANTE", "d"))));

        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01").param("hasta", "2026-09-30").with(clave()))
                .andExpect(jsonPath("$.datos.limpio").doesNotExist())
                .andExpect(jsonPath("$.datos.problemas[0].comprobanteId").doesNotExist())
                .andExpect(jsonPath("$.datos.problemas[0].nombreArchivo").doesNotExist());
    }

    @Test void pasaAlCasoDeUsoLasDosFechasTalCual() throws Exception {
        when(integridad.verificar(any(), any())).thenReturn(new Informe(DESDE, HASTA, 0, List.of()));

        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-30").param("hasta", "2026-09-30").with(clave())).andExpect(status().isOk());

        verify(integridad).verificar(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30));
    }

    @Test void unRangoAlReversEs400ConSuCodigo() throws Exception {
        when(integridad.verificar(any(), any())).thenThrow(new DomainException("RANGO_INVALIDO", "El rango de fechas es obligatorio y desde ≤ hasta"));

        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-30").param("hasta", "2026-09-01").with(clave()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));
    }

    @Test void sinFechasOConUnaFechaMalEscritaEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/admin/integridad").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-09-01").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/integridad").param("hasta", "2026-09-30").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/integridad").param("desde", "01/09/2026").param("hasta", "2026-09-30").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/integridad").param("desde", "2026-02-30").param("hasta", "2026-09-30").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/integridad").param("desde", "ayer").param("hasta", "hoy").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(integridad);
    }
}
