package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.PersonalizarPdfUseCase;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.PlantillaPdf;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PersonalizacionPdfController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class PersonalizacionPdfControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PersonalizarPdfUseCase personalizar;
    UUID tenant = UUID.randomUUID();
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};

    @Test void guardaElDiseñoEnSnakeCaseYDevuelveSiHayLogo() throws Exception {
        PersonalizacionPdf guardada = new PersonalizacionPdf(PlantillaPdf.MODERNO, "#1F5F4A", tenant + "/logo.png", "Gracias", "Obs");
        when(personalizar.actualizar(eq(tenant), any())).thenReturn(guardada);
        mvc.perform(put("/v1/empresa/personalizacion-pdf").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content("{\"plantilla\":\"moderno\",\"color_primario\":\"#1f5f4a\",\"pie_de_pagina\":\"Gracias\",\"observaciones_por_defecto\":\"Obs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.plantilla").value("moderno"))
                .andExpect(jsonPath("$.datos.color_primario").value("#1F5F4A"))
                .andExpect(jsonPath("$.datos.tiene_logo").value(true));
        ArgumentCaptor<PersonalizacionPdf> cap = ArgumentCaptor.forClass(PersonalizacionPdf.class);
        verify(personalizar).actualizar(eq(tenant), cap.capture());
        assertThat(cap.getValue().plantilla()).isEqualTo(PlantillaPdf.MODERNO);
        assertThat(cap.getValue().colorPrimario()).isEqualTo("#1F5F4A");
        assertThat(cap.getValue().logoKey()).isNull();

        mvc.perform(put("/v1/empresa/personalizacion-pdf").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content("{\"plantilla\":\"neon\",\"color_primario\":\"azul\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores.plantilla").exists())
                .andExpect(jsonPath("$.errores.colorPrimario").exists());
    }

    @Test void laVistaPreviaCombinaLoGuardadoConLosParametrosYNoSeCachea() throws Exception {
        when(personalizar.obtener(tenant)).thenReturn(new PersonalizacionPdf(PlantillaPdf.CLASICO, "#111111", null, "Pie guardado", null));
        when(personalizar.vistaPrevia(eq(tenant), any())).thenReturn("%PDF-1.4".getBytes());
        mvc.perform(get("/v1/empresa/personalizacion-pdf/vista-previa").requestAttr(TenantActual.ATRIBUTO, tenant).param("plantilla", "corporativo").param("color_primario", "#C8552B"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes("%PDF-1.4".getBytes()));
        ArgumentCaptor<PersonalizacionPdf> cap = ArgumentCaptor.forClass(PersonalizacionPdf.class);
        verify(personalizar).vistaPrevia(eq(tenant), cap.capture());
        assertThat(cap.getValue().plantilla()).isEqualTo(PlantillaPdf.CORPORATIVO);
        assertThat(cap.getValue().colorPrimario()).isEqualTo("#C8552B");
        assertThat(cap.getValue().pieDePagina()).isEqualTo("Pie guardado");
    }

    @Test void elLogoSeCargaPorMultipartSeDescargaConSuTipoYSeBorra() throws Exception {
        when(personalizar.cargarLogo(eq(tenant), any())).thenReturn(PersonalizacionPdf.porDefecto().conLogo("k/logo.png"));
        mvc.perform(multipart("/v1/empresa/logo").file(new MockMultipartFile("archivo", "logo.png", "image/png", PNG)).with(r -> { r.setMethod("PUT"); return r; })
                        .requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.tiene_logo").value(true));
        verify(personalizar).cargarLogo(tenant, PNG);

        when(personalizar.logo(tenant)).thenReturn(PNG);
        mvc.perform(get("/v1/empresa/logo").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));

        when(personalizar.borrarLogo(tenant)).thenReturn(PersonalizacionPdf.porDefecto());
        mvc.perform(delete("/v1/empresa/logo").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.tiene_logo").value(false));

        doThrow(new DomainException("LOGO_INVALIDO", "El logo debe ser PNG o JPEG")).when(personalizar).cargarLogo(eq(tenant), any());
        mvc.perform(multipart("/v1/empresa/logo").file(new MockMultipartFile("archivo", "l.svg", "image/svg+xml", "<svg/>".getBytes())).with(r -> { r.setMethod("PUT"); return r; })
                        .requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LOGO_INVALIDO"));
    }
}
