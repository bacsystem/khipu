package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FacturaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class FacturaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean EmitirComprobanteUseCase emitir;
    @MockBean EnviarDocumentoUseCase enviar;
    @MockBean ConsultarComprobanteUseCase consultar;

    UUID tenant = UUID.randomUUID();

    static Comprobante aceptado(UUID tenant) {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        c.asignarNumero(601, "20100066603"); c.firmar("HASH", "k.xml"); c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "k.zip");
        return c;
    }

    String cuerpo = """
        {"serie":"F001","fecha_emision":"2026-09-13","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234567","razon_social":"CLIENTE SAC","direccion":"AV 1"},
         "items":[{"codigo":"P1","descripcion":"Prod","unidad":"NIU","cantidad":1,"precio_unitario":118.00,"tipo_afectacion_igv":"10"}]}
        """;

    @Test void crearFacturaDevuelve201ConSobre() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(aceptado(tenant));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.serie").value("F001"))
                .andExpect(jsonPath("$.datos.numero").value(601))
                .andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.hash").value("HASH"))
                .andExpect(jsonPath("$.datos.cdr.codigo").value("0"))
                .andExpect(jsonPath("$.datos.totales.total").value(118.00))
                .andExpect(jsonPath("$.datos.enlaces.xml").exists());
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().enviarAutomatico()).isTrue();
        assertThat(cap.getValue().items().get(0).afectacion()).isEqualTo(TipoAfectacionIgv.GRAVADO);
    }

    @Test void validacionDeDtoDevuelve422() throws Exception {
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"serie\":\"F001\",\"items\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.cliente").exists())
                .andExpect(jsonPath("$.errores.items").exists());
    }

    @Test void errorDeDominioSeMapea() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new DomainException("DUPLICADO", "Ya existe F001-1"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test void descargaXml() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        when(consultar.xml(tenant, c.id())).thenReturn("<Invoice/>".getBytes());
        mvc.perform(get("/v1/facturas/{id}/xml", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20100066603-01-F001-601.xml\""))
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string("<Invoice/>"));
    }

    @Test void enviarManual() throws Exception {
        Comprobante c = aceptado(tenant);
        when(enviar.enviar(tenant, c.id())).thenReturn(c);
        mvc.perform(post("/v1/facturas/{id}/enviar", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"));
    }

    @Test void listarDevuelveListaYTotalEnHeader() throws Exception {
        when(consultar.listar(eq(tenant), isNull(), eq(1), eq(20))).thenReturn(List.of(aceptado(tenant)));
        when(consultar.contar(tenant, null)).thenReturn(126L);
        mvc.perform(get("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(header().string(FacturaController.TOTAL_HEADER, "126"))
                .andExpect(jsonPath("$.datos[0].serie").value("F001"));
    }

    @Test void listarAcotaPorPagina() throws Exception {
        when(consultar.listar(eq(tenant), isNull(), eq(1), eq(100))).thenReturn(List.of());
        mvc.perform(get("/v1/facturas?pagina=0&por_pagina=500").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(consultar).listar(tenant, null, 1, 100);
    }

    @Test void obtenerPorId() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        mvc.perform(get("/v1/facturas/{id}", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.id").value(c.id().toString()))
                .andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"));
    }

    @Test void obtenerInexistenteEs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(consultar.obtener(tenant, id)).thenThrow(new DomainException("NO_ENCONTRADO", "x"));
        mvc.perform(get("/v1/facturas/{id}", id).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void descargaCdr() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        when(consultar.cdr(tenant, c.id())).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/v1/facturas/{id}/cdr", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"R-20100066603-01-F001-601.zip\""))
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test void descargaCdrComoXmlExtraido() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        when(consultar.cdrXml(tenant, c.id())).thenReturn("<ar/>".getBytes());
        mvc.perform(get("/v1/facturas/{id}/cdr?formato=xml", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"R-20100066603-01-F001-601.xml\""))
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string("<ar/>"));
    }

    @Test void formatoDesconocidoDeCdrEs400() throws Exception {
        Comprobante c = aceptado(tenant);
        mvc.perform(get("/v1/facturas/{id}/cdr?formato=pdf", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        verify(consultar, never()).cdr(any(), any());
    }

    @Test void cdrCorruptoEs500YNo422() throws Exception {
        Comprobante c = aceptado(tenant);
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        when(consultar.cdrXml(tenant, c.id())).thenThrow(new pe.factura.domain.DomainException("CDR_CORRUPTO", "El ZIP del CDR no contiene un XML"));
        mvc.perform(get("/v1/facturas/{id}/cdr?formato=xml", c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("CDR_CORRUPTO"));
    }

    @Test void jsonMalformadoEs400() throws Exception {
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"serie\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test void claveDuplicadaEnBaseDeDatosEs409() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new org.springframework.dao.DuplicateKeyException("ux documento (tenant_id, tipo, serie, numero)"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value("error"))
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe un documento con esa serie y número"));
    }

    @Test void idQueNoEsUuidEs400YNo500() throws Exception {
        mvc.perform(get("/v1/facturas/f-error").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test void rutaInexistenteEs404YNo500() throws Exception {
        mvc.perform(get("/v1/no-existe").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RUTA_INEXISTENTE"));
    }

    @Test void errorInternoEs500SinDetalle() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new IllegalStateException("detalle secreto"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("INTERNO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle secreto"))));
    }
}
