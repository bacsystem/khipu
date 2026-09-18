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

    /** Rechazado por SOAPFault: tiene código y descripción de SUNAT pero ninguna constancia que descargar. */
    static Comprobante rechazadoPorFault(UUID tenant) {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        c.asignarNumero(602, "20100066603"); c.firmar("HASH", "k.xml");
        c.rechazarPorFault("1033", "El comprobante fue registrado previamente con otros datos");
        return c;
    }

    @Test void enlaceCdrSoloCuandoHayConstancia() throws Exception {
        Comprobante conCdr = aceptado(tenant), sinCdr = rechazadoPorFault(tenant);
        when(consultar.obtener(tenant, conCdr.id())).thenReturn(conCdr);
        when(consultar.obtener(tenant, sinCdr.id())).thenReturn(sinCdr);
        mvc.perform(get("/v1/facturas/{id}", conCdr.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(jsonPath("$.datos.enlaces.cdr").value("/v1/facturas/" + conCdr.id() + "/cdr"));
        mvc.perform(get("/v1/facturas/{id}", sinCdr.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(jsonPath("$.datos.estado_documento").value("RECHAZADO"))
                .andExpect(jsonPath("$.datos.cdr.codigo").value("1033"))
                .andExpect(jsonPath("$.datos.enlaces.xml").exists())
                .andExpect(jsonPath("$.datos.enlaces.cdr").doesNotExist());
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

    @Test void facturaAlCreditoEntraYSaleConCuotas() throws Exception {
        String conCredito = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"forma_pago\":{\"tipo\":\"credito\",\"monto_pendiente\":118.00,"
                + "\"cuotas\":[{\"monto\":59.00,\"vencimiento\":\"2026-10-13\"},{\"monto\":59.00,\"vencimiento\":\"2026-11-13\"}]},");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                c.receptor(), c.items(), FormaPago.credito(new BigDecimal("118.00"), List.of(
                        new FormaPago.Cuota(new BigDecimal("59.00"), LocalDate.of(2026, 10, 13)), new FormaPago.Cuota(new BigDecimal("59.00"), LocalDate.of(2026, 11, 13)))),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conCredito))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.forma_pago.tipo").value("credito"))
                .andExpect(jsonPath("$.datos.forma_pago.monto_pendiente").value(118.00))
                .andExpect(jsonPath("$.datos.forma_pago.cuotas[0].id").value("Cuota001"))
                .andExpect(jsonPath("$.datos.forma_pago.cuotas[1].vencimiento").value("2026-11-13"));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().formaPago().esCredito()).isTrue();
        assertThat(cap.getValue().formaPago().cuotas()).hasSize(2);
    }

    @Test void sinFormaPagoEsContado() throws Exception {
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(aceptado(tenant));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.forma_pago.tipo").value("contado"))
                .andExpect(jsonPath("$.datos.forma_pago.cuotas").isEmpty());
    }

    /** Las cuotas que no cuadran se rechazan al construir el comando (antes del caso de uso) con el código SUNAT en el mensaje. */
    @Test void cuotasQueNoSumanElPendienteEs422ConCodigoSunat() throws Exception {
        String malCuadrado = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"forma_pago\":{\"tipo\":\"credito\",\"monto_pendiente\":118.00,"
                + "\"cuotas\":[{\"monto\":50.00,\"vencimiento\":\"2026-10-13\"}]},");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(malCuadrado))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FORMA_PAGO_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("3319")));
        org.mockito.Mockito.verify(emitir, never()).emitirFactura(any(), any());
    }

    /** Sin Bean Validation en forma_pago: el formato también lo responde el dominio con el código SUNAT, un solo contrato de error. */
    @Test void cuotaConMontoNegativoRespondeCodigoSunat3253() throws Exception {
        String negativa = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"forma_pago\":{\"tipo\":\"credito\",\"monto_pendiente\":118.00,"
                + "\"cuotas\":[{\"monto\":-1.00,\"vencimiento\":\"2026-10-13\"}]},");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(negativa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("FORMA_PAGO_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("3253")));
    }

    @Test void descuentosEntranYSalenConCodigoSunat() throws Exception {
        String conDescuentos = cuerpo
                .replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"descuento\":{\"porcentaje\":10}}")
                .replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"descuento_global\":{\"monto\":5.00,\"afecta_base_igv\":false},");
        Comprobante c = aceptado(tenant);
        Comprobante conDesc = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(BigDecimal.TEN, true))),
                FormaPago.contado(), Descuento.monto(new BigDecimal("5.00"), false), Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(conDesc);
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conDescuentos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.items[0].descuento.codigo").value("00"))
                .andExpect(jsonPath("$.datos.items[0].descuento.monto").value(10.00))
                .andExpect(jsonPath("$.datos.items[0].valor_venta").value(90.00))
                .andExpect(jsonPath("$.datos.items[0].igv").value(16.20))
                .andExpect(jsonPath("$.datos.items[0].precio_venta").value(106.20))
                .andExpect(jsonPath("$.datos.totales.descuento_global.codigo").value("03"))
                .andExpect(jsonPath("$.datos.totales.total_descuentos").value(5.00))
                .andExpect(jsonPath("$.datos.totales.total_precio_venta").value(106.20))
                .andExpect(jsonPath("$.datos.totales.total").value(101.20));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().items().get(0).descuento().valor()).isEqualByComparingTo("10");
        assertThat(cap.getValue().descuentoGlobal().afectaBaseIgv()).isFalse();
    }

    @Test void descuentoConPorcentajeYMontoEs422() throws Exception {
        String ambiguo = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"descuento\":{\"porcentaje\":10,\"monto\":5}}");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(ambiguo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DESCUENTO_INVALIDO"));
        org.mockito.Mockito.verify(emitir, never()).emitirFactura(any(), any());
    }

    @Test void cargosEntranYSalenConCodigoSunat() throws Exception {
        String conCargos = cuerpo
                .replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"cargos\":[{\"monto\":10.00},{\"monto\":5.00,\"afecta_base_igv\":false}]}")
                .replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"cargos\":[{\"monto\":20.00},{\"porcentaje\":10,\"motivo\":\"recargo_consumo\"}],");
        Comprobante c = aceptado(tenant);
        // Sin afecta_base_igv el cargo afecta la base: 47 en línea, 49 global; con motivo, 46.
        Comprobante conCargo = Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false,
                        List.of(Cargo.monto("47", new BigDecimal("10.00")), Cargo.monto("48", new BigDecimal("5.00"))))),
                FormaPago.contado(), null, List.of(Cargo.monto("49", new BigDecimal("20.00")), Cargo.porcentaje("46", BigDecimal.TEN)), null, null, null, List.of(),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(conCargo);
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conCargos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.items[0].cargos[0].codigo").value("47"))
                .andExpect(jsonPath("$.datos.items[0].cargos[0].afecta_base_igv").value(true))
                .andExpect(jsonPath("$.datos.items[0].cargos[0].motivo").doesNotExist())
                .andExpect(jsonPath("$.datos.items[0].cargos[1].codigo").value("48"))
                .andExpect(jsonPath("$.datos.items[0].cargos[1].monto").value(5.00))
                .andExpect(jsonPath("$.datos.items[0].cargos[1].afecta_base_igv").value(false))
                .andExpect(jsonPath("$.datos.items[0].valor_venta").value(110.00))
                .andExpect(jsonPath("$.datos.items[0].precio_venta").value(134.80))               // 110 + 19.80 + 5
                .andExpect(jsonPath("$.datos.totales.cargos[0].codigo").value("49"))
                .andExpect(jsonPath("$.datos.totales.cargos[0].afecta_base_igv").value(true))
                .andExpect(jsonPath("$.datos.totales.cargos[1].codigo").value("46"))
                .andExpect(jsonPath("$.datos.totales.cargos[1].motivo").value("recargo_consumo"))
                .andExpect(jsonPath("$.datos.totales.cargos[1].monto").value(11.00))            // 10 % de la base onerosa bruta 110
                .andExpect(jsonPath("$.datos.totales.gravado").value(130.00))                    // 110 + 20
                .andExpect(jsonPath("$.datos.totales.total_cargos").value(16.00))                // 5 + 11
                .andExpect(jsonPath("$.datos.totales.total_precio_venta").value(153.40))         // 130 + 23.40
                .andExpect(jsonPath("$.datos.totales.total").value(169.40));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().items().get(0).cargos()).containsExactly(Cargo.monto("47", new BigDecimal("10.00")), Cargo.monto("48", new BigDecimal("5.00")));
        assertThat(cap.getValue().cargos()).containsExactly(Cargo.monto("49", new BigDecimal("20.00")), Cargo.porcentaje("46", BigDecimal.TEN));
    }

    @Test void cargoAmbiguoOConMotivoMalUbicadoEs422() throws Exception {
        String ambiguo = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"cargos\":[{\"porcentaje\":10,\"monto\":5}]}");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(ambiguo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"));
        String recargoEnLinea = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"cargos\":[{\"monto\":5,\"motivo\":\"recargo_consumo\"}]}");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(recargoEnLinea))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("4268")));
        String recargoConIgv = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"cargos\":[{\"monto\":5,\"motivo\":\"recargo_consumo\",\"afecta_base_igv\":true}],");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(recargoConIgv))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"));
        String motivoDesconocido = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"cargos\":[{\"monto\":5,\"motivo\":\"fise\"}],");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(motivoDesconocido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("recargo_consumo")));
        org.mockito.Mockito.verify(emitir, never()).emitirFactura(any(), any());
    }

    @Test void documentosRelacionadosEntranYSalen() throws Exception {
        String conRefs = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"orden_compra\":\"OC-2026-0457\","
                + "\"guias\":[{\"tipo\":\"09\",\"numero\":\"T001-123\"}],\"documentos_relacionados\":[{\"tipo\":\"05\",\"numero\":\"SCOP-8841203\"}],");
        Comprobante c = aceptado(tenant);
        Referencias refs = new Referencias("OC-2026-0457", List.of(new GuiaRelacionada("09", "T001-123")), List.of(new DocumentoRelacionado("05", "SCOP-8841203")));
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(),
                c.items(), FormaPago.contado(), null, List.of(), null, null, null, List.of(), refs, Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conRefs))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.referencias.orden_compra").value("OC-2026-0457"))
                .andExpect(jsonPath("$.datos.referencias.guias[0].tipo").value("09"))
                .andExpect(jsonPath("$.datos.referencias.guias[0].numero").value("T001-123"))
                .andExpect(jsonPath("$.datos.referencias.documentos_relacionados[0].tipo").value("05"));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().referencias()).isEqualTo(refs);
        // Sin referencias, el bloque no aparece en la respuesta.
        when(consultar.obtener(tenant, c.id())).thenReturn(c);
        mvc.perform(get("/v1/facturas/" + c.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.referencias").doesNotExist());
    }

    @Test void documentoRelacionadoInvalidoEs422() throws Exception {
        String guiaMal = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"guias\":[{\"tipo\":\"09\",\"numero\":\"F001-123\"}],");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(guiaMal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_RELACIONADO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("4006")));
        String anticipoComoOtro = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"documentos_relacionados\":[{\"tipo\":\"02\",\"numero\":\"F001-1\"}],");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(anticipoComoOtro))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['documentosRelacionados[0].tipo']").exists());
        String ordenLarga = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"orden_compra\":\"" + "X".repeat(21) + "\",");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(ordenLarga))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['ordenCompra']").exists());
        org.mockito.Mockito.verify(emitir, never()).emitirFactura(any(), any());
    }

    @Test void camposOpcionalesEntranYSalen() throws Exception {
        String con = cuerpo
                .replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"codigo_sunat\":\"15101505\",\"gtin\":{\"tipo\":\"GTIN-13\",\"codigo\":\"7750182000123\"}}")
                .replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"fecha_vencimiento\":\"2026-10-13\",\"redondeo\":-0.37,");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), LocalDate.of(2026, 10, 13), "PEN", "0101", c.receptor(),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.37"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), new CodigoProductoSunat("15101505"), new Gtin("GTIN-13", "7750182000123"))),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, new BigDecimal("-0.37"), Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(con))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.fecha_vencimiento").value("2026-10-13"))
                .andExpect(jsonPath("$.datos.items[0].codigo_sunat").value("15101505"))
                .andExpect(jsonPath("$.datos.items[0].gtin.tipo").value("GTIN-13"))
                .andExpect(jsonPath("$.datos.totales.redondeo").value(-0.37))
                .andExpect(jsonPath("$.datos.totales.total").value(118.00));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(cap.getValue().redondeo()).isEqualByComparingTo("-0.37");
        assertThat(cap.getValue().items().get(0).gtin()).isEqualTo(new Gtin("GTIN-13", "7750182000123"));
    }

    @Test void camposOpcionalesInvalidosSon422() throws Exception {
        String gtinCorto = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"gtin\":{\"tipo\":\"GTIN-13\",\"codigo\":\"775018\"}}");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(gtinCorto))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ITEM_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("4334")));
        String codigoSunatMal = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"codigo_sunat\":\"1510\"}");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(codigoSunatMal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['items[0].codigoSunat']").exists());
        // fecha_vencimiento anterior a la emisión y redondeo fuera de ±1 los rechaza el dominio (FECHA_INVALIDA, REDONDEO_INVALIDO): ver CamposOpcionalesTest.
        org.mockito.Mockito.verify(emitir, never()).emitirFactura(any(), any());
    }

    @Test void lineaGratuitaAceptadaYMarcadaEnLaRespuesta() throws Exception {
        String conBonificacion = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\"},{\"descripcion\":\"Bonificación\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":10.00,\"tipo_afectacion_igv\":\"15\"}");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(),
                List.of(c.items().get(0), new Item(null, "Bonificación", "NIU", new BigDecimal("2"), new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION)),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conBonificacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.items[1].gratuita").value(true))
                .andExpect(jsonPath("$.datos.items[1].precio_venta").value(0.00))
                .andExpect(jsonPath("$.datos.items[1].igv").value(3.60))
                .andExpect(jsonPath("$.datos.totales.gratuito").value(20.00))
                .andExpect(jsonPath("$.datos.totales.igv_gratuitas").value(3.60))
                .andExpect(jsonPath("$.datos.totales.total").value(118.00));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().items().get(1).afectacion()).isEqualTo(TipoAfectacionIgv.GRAVADO_BONIFICACION);
    }

    @Test void afectacionNoSoportadaEs422DeValidacion() throws Exception {
        String ivap = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"", "\"tipo_afectacion_igv\":\"17\"");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(ivap))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void detraccionEnPenCalculaElMontoYLoDevuelve() throws Exception {
        String conDetraccion = cuerpo.replace("\"tipo_operacion\":\"0101\"", "\"tipo_operacion\":\"1001\",\"detraccion\":{\"codigo_bien_servicio\":\"022\",\"porcentaje\":12,\"cuenta_banco_nacion\":\"00-000-123456\"}");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "1001", c.receptor(), c.items(),
                FormaPago.contado(), null, new Detraccion("022", new BigDecimal("12"), new BigDecimal("14.00"), "00-000-123456", null),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conDetraccion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.detraccion.codigo_bien_servicio").value("022"))
                .andExpect(jsonPath("$.datos.detraccion.monto").value(14.00))
                .andExpect(jsonPath("$.datos.detraccion.medio_pago").value("001"))
                .andExpect(jsonPath("$.datos.detraccion.descripcion").isNotEmpty());
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().detraccion().monto()).isNull();   // lo completa el dominio contra el importe total
    }

    @Test void detraccionEnDolaresSinMontoEs422() throws Exception {
        String usd = cuerpo.replace("\"moneda\":\"PEN\"", "\"moneda\":\"USD\"")
                .replace("\"tipo_operacion\":\"0101\"", "\"tipo_operacion\":\"1001\",\"detraccion\":{\"codigo_bien_servicio\":\"022\",\"porcentaje\":12,\"cuenta_banco_nacion\":\"00-000-123456\"}");
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new DomainException("DETRACCION_INVALIDA", "3208 - En facturas en USD debe indicar el monto de la detracción en soles"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(usd))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DETRACCION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("3208")));
    }

    @Test void retencionYPercepcionEntranYSalen() throws Exception {
        String conAmbas = cuerpo.replace("\"tipo_operacion\":\"0101\"", "\"tipo_operacion\":\"2001\",\"retencion_igv\":{},\"percepcion\":{\"regimen\":\"51\"}");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "2001", c.receptor(), c.items(),
                FormaPago.contado(), null, null, new RetencionIgv(null, null), new Percepcion("51", null, null, null),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conAmbas))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.retencion_igv.monto").value(3.54))
                .andExpect(jsonPath("$.datos.retencion_igv.neto_cobrar").value(114.46))
                .andExpect(jsonPath("$.datos.percepcion.regimen").value("51"))
                .andExpect(jsonPath("$.datos.percepcion.monto").value(2.36))
                .andExpect(jsonPath("$.datos.percepcion.total_con_percepcion").value(120.36));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().retencionIgv().porcentaje()).isEqualByComparingTo("3");
        assertThat(cap.getValue().percepcion().regimen()).isEqualTo("51");
    }

    @Test void percepcionSinOperacion2001Es422ConCodigoSunat() throws Exception {
        String mal = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"percepcion\":{\"regimen\":\"51\"},");
        when(emitir.emitirFactura(eq(tenant), any())).thenThrow(new DomainException("PERCEPCION_INVALIDA", "3308 - Solo se informa percepción con tipo de operación 2001, no 0101"));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(mal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PERCEPCION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("3308")));
    }

    @Test void iscEIcbperEntranYSalen() throws Exception {
        String conTributos = cuerpo.replace("\"tipo_afectacion_igv\":\"10\"}", "\"tipo_afectacion_igv\":\"10\",\"isc\":{\"sistema\":\"01\",\"tasa\":35}},{\"descripcion\":\"Bolsa\",\"unidad\":\"NIU\",\"cantidad\":2,\"precio_unitario\":0.618,\"tipo_afectacion_igv\":\"10\",\"icbper\":true}");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("159.30"), TipoAfectacionIgv.GRAVADO, null, new Isc("01", new BigDecimal("35"), null), false),
                        new Item(null, "Bolsa", "NIU", new BigDecimal("2"), new BigDecimal("0.618"), TipoAfectacionIgv.GRAVADO, null, null, true)),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conTributos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.items[0].isc.sistema").value("01"))
                .andExpect(jsonPath("$.datos.items[0].isc.monto").value(35.00))
                .andExpect(jsonPath("$.datos.items[1].icbper").value(1.00))
                .andExpect(jsonPath("$.datos.totales.isc").value(35.00))
                .andExpect(jsonPath("$.datos.totales.icbper").value(1.00));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().items().get(0).isc().tasa()).isEqualByComparingTo("35");
        assertThat(cap.getValue().items().get(1).icbper()).isTrue();
    }

    @Test void anticiposEntranYSalen() throws Exception {
        String conAnticipo = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"anticipos\":[{\"serie\":\"F001\",\"numero\":10,\"monto\":30.00,\"fecha_pago\":\"2026-09-01\"}],");
        Comprobante c = aceptado(tenant);
        when(emitir.emitirFactura(eq(tenant), any())).thenReturn(Comprobante.crearFactura(tenant, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", c.receptor(), c.items(),
                FormaPago.contado(), null, null, null, null, List.of(new Anticipo("F001", 10, new BigDecimal("30.00"), null, LocalDate.of(2026, 9, 1))),
                Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"))));
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conAnticipo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.anticipos[0].comprobante").value("F001-10"))
                .andExpect(jsonPath("$.datos.anticipos[0].monto").value(30.00))
                .andExpect(jsonPath("$.datos.anticipos[0].importe_pagado").value(35.40))
                .andExpect(jsonPath("$.datos.anticipos[0].afectacion").value("gravado"))
                .andExpect(jsonPath("$.datos.anticipos[0].codigo_sunat").value("04"))
                .andExpect(jsonPath("$.datos.anticipos[0].fecha_pago").value("2026-09-01"))
                .andExpect(jsonPath("$.datos.totales.gravado").value(70.00))
                .andExpect(jsonPath("$.datos.totales.igv").value(12.60))
                .andExpect(jsonPath("$.datos.totales.total_precio_venta").value(118.00))
                .andExpect(jsonPath("$.datos.totales.total_anticipos").value(35.40))
                .andExpect(jsonPath("$.datos.totales.total").value(82.60));
        ArgumentCaptor<EmitirFacturaCommand> cap = ArgumentCaptor.forClass(EmitirFacturaCommand.class);
        org.mockito.Mockito.verify(emitir).emitirFactura(eq(tenant), cap.capture());
        assertThat(cap.getValue().anticipos()).singleElement().satisfies(a -> {
            assertThat(a.comprobante()).isEqualTo("F001-10");
            assertThat(a.afectacion()).isEqualTo(Anticipo.Afectacion.GRAVADO);
            assertThat(a.fechaPago()).isEqualTo(LocalDate.of(2026, 9, 1));
        });
    }

    @Test void anticipoConSerieInvalidaEs422DeValidacion() throws Exception {
        String mal = cuerpo.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"anticipos\":[{\"serie\":\"B001\",\"numero\":10,\"monto\":30.00}],");
        mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(mal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores['anticipos[0].serie']").exists());
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
