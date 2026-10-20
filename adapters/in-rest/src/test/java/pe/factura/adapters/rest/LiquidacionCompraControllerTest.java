package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirLiquidacionCommand;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /v1/liquidaciones-compra} (#327): el pedido llega al caso de uso tal cual y la respuesta trae el vendedor, la retención y el neto. */
@WebMvcTest(controllers = LiquidacionCompraController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class LiquidacionCompraControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EmitirComprobanteUseCase emitir;
    UUID tenant = UUID.randomUUID();

    static final String CUERPO = """
        {"serie":"L001","fecha_emision":"2026-09-13","moneda":"PEN",
         "vendedor":{"tipo_doc":"1","num_doc":"45678912","nombre":"JUAN QUISPE MAMANI","direccion":"Caserío Santa Rosa s/n","ubigeo":"060101","condicion":"02"},
         "lugar_operacion":{"direccion":"Fundo Santa Rosa, km 12","ubigeo":"060801","condicion":"03"},
         "items":[{"codigo":"CAF-01","descripcion":"Café pergamino","unidad":"KGM","cantidad":10,"precio_unitario":118,"tipo_afectacion_igv":"10","codigo_sunat":"50201706"}],
         "guias":[{"tipo":"31","numero":"V001-45"}],"documentos_relacionados":[{"tipo":"99","numero":"ACTA-7"}],"enviar_automatico":false,"observaciones":"Compra en chacra"}
        """;

    static Comprobante aceptada(UUID tenant) {
        Comprobante c = Comprobante.liquidacionCompra(tenant, "L001", LocalDate.of(2026, 9, 13), "PEN", null, new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n"),
                        new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12", "060801", "03"), null),
                        List.of(new Item("CAF-01", "Café pergamino", "KGM", BigDecimal.TEN, new BigDecimal("118"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null)))
                .crear(Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        c.asignarNumero(15, "20100066603"); c.firmar("HASH", "k.xml"); c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "El comprobante de liquidacion de compra numero L001-15, ha sido aceptada", List.of()), "k.zip");
        return c;
    }

    @Test void emiteYRespondeElVendedorLaRetencionYElNeto() throws Exception {
        when(emitir.emitirLiquidacionCompra(eq(tenant), any())).thenReturn(aceptada(tenant));
        mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tipo").value("04"))
                .andExpect(jsonPath("$.datos.serie").value("L001"))
                .andExpect(jsonPath("$.datos.numero").value(15))
                .andExpect(jsonPath("$.datos.nombre_archivo").value("20100066603-04-L001-15"))
                .andExpect(jsonPath("$.datos.tipo_operacion").value("0501"))
                .andExpect(jsonPath("$.datos.receptor.tipo_doc").value("1"))
                .andExpect(jsonPath("$.datos.receptor.razon_social").value("JUAN QUISPE MAMANI"))
                .andExpect(jsonPath("$.datos.receptor.direccion").value("Caserío Santa Rosa s/n"))
                .andExpect(jsonPath("$.datos.liquidacion.ubigeo_vendedor").value("060101"))
                .andExpect(jsonPath("$.datos.liquidacion.condicion_vendedor").value("02"))
                .andExpect(jsonPath("$.datos.liquidacion.lugar_operacion.ubigeo").value("060801"))
                .andExpect(jsonPath("$.datos.liquidacion.lugar_operacion.condicion").value("03"))
                .andExpect(jsonPath("$.datos.liquidacion.subtotal").value(1180.00))
                .andExpect(jsonPath("$.datos.liquidacion.igv_retenido").value(180.00))
                .andExpect(jsonPath("$.datos.liquidacion.retencion_renta.tasa").value(1.5))
                .andExpect(jsonPath("$.datos.liquidacion.retencion_renta.base").value(1000.00))
                .andExpect(jsonPath("$.datos.liquidacion.retencion_renta.monto").value(15.00))
                .andExpect(jsonPath("$.datos.liquidacion.neto_pagar").value(985.00))
                .andExpect(jsonPath("$.datos.totales.total").value(985.00))
                .andExpect(jsonPath("$.datos.fecha_limite_envio").value("2026-09-14"))
                .andExpect(jsonPath("$.datos.enlaces.xml").value(org.hamcrest.Matchers.startsWith("/v1/facturas/")));
        ArgumentCaptor<EmitirLiquidacionCommand> cap = ArgumentCaptor.forClass(EmitirLiquidacionCommand.class);
        verify(emitir).emitirLiquidacionCompra(eq(tenant), cap.capture());
        EmitirLiquidacionCommand cmd = cap.getValue();
        assertThat(cmd.vendedor()).isEqualTo(new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n"));
        assertThat(cmd.liquidacion()).isEqualTo(new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12", "060801", "03"), null));
        assertThat(cmd.liquidacion().tasaRetencionRenta()).as("por defecto la vigente").isEqualByComparingTo("1.5");
        assertThat(cmd.items()).singleElement().satisfies(i -> {
            assertThat(i.codigoSunat().codigo()).isEqualTo("50201706");
            assertThat(i.afectacion()).isEqualTo(TipoAfectacionIgv.GRAVADO);
        });
        assertThat(cmd.referencias().guias()).containsExactly(new GuiaRelacionada("31", "V001-45"));
        assertThat(cmd.referencias().otros()).containsExactly(new DocumentoRelacionado("99", "ACTA-7"));
        assertThat(cmd.enviarAutomatico()).isFalse();
        assertThat(cmd.observaciones()).isEqualTo("Compra en chacra");
    }

    @Test void laTasaDeLaRetencionSePuedeInformar() throws Exception {
        when(emitir.emitirLiquidacionCompra(eq(tenant), any())).thenReturn(aceptada(tenant));
        mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(CUERPO.replace("\"enviar_automatico\":false", "\"retencion_renta\":{\"tasa\":1.5}")))
                .andExpect(status().isCreated());
        ArgumentCaptor<EmitirLiquidacionCommand> cap = ArgumentCaptor.forClass(EmitirLiquidacionCommand.class);
        verify(emitir).emitirLiquidacionCompra(eq(tenant), cap.capture());
        assertThat(cap.getValue().liquidacion().tasaRetencionRenta()).isEqualByComparingTo("1.5");
        assertThat(cap.getValue().enviarAutomatico()).isTrue();
    }

    @Test void validaElFormatoAntesDeLlegarAlCasoDeUso() throws Exception {
        for (String[] caso : new String[][]{
                {"\"serie\":\"L001\"", "\"serie\":\"F001\"", "serie"},
                {"\"tipo_doc\":\"1\"", "\"tipo_doc\":\"6\"", "vendedor.tipoDoc"},
                {"\"ubigeo\":\"060101\"", "\"ubigeo\":\"6010\"", "vendedor.ubigeo"},
                {"\"condicion\":\"03\"", "\"condicion\":\"3\"", "lugarOperacion.condicion"},
                {"\"tipo_afectacion_igv\":\"10\"", "\"tipo_afectacion_igv\":\"15\"", "items[0].tipoAfectacionIgv"},
                {",\"codigo_sunat\":\"50201706\"", "", "items[0].codigoSunat"}}) {
            mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO.replace(caso[0], caso[1])))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores['" + caso[2] + "']").exists());
        }
        mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(CUERPO.replaceAll("\"lugar_operacion\":\\{[^}]*\\},", "")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores['lugarOperacion']").exists());
        verifyNoInteractions(emitir);
    }

    @Test void unaReglaDeDominioRespondeSuCodigo() throws Exception {
        when(emitir.emitirLiquidacionCompra(eq(tenant), any())).thenThrow(new DomainException("LIQUIDACION_INVALIDA", "2917 - El ubigeo del lugar de la operación no existe en el catálogo 13 (INEI): 999999"));
        mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LIQUIDACION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("2917")));
    }

    @Test void conIdempotencyKeyUnReintentoEs200() throws Exception {
        when(emitir.emitirLiquidacionCompra(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(aceptada(tenant), true));
        mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .header("Idempotency-Key", "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f").content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.numero").value(15));
        ArgumentCaptor<Idempotencia> cap = ArgumentCaptor.forClass(Idempotencia.class);
        verify(emitir).emitirLiquidacionCompra(eq(tenant), any(), cap.capture());
        assertThat(cap.getValue().clave()).isEqualTo("5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
    }

    @Test void calcularNoEmiteYNoTraeEnlaces() throws Exception {
        Comprobante c = aceptada(tenant);
        when(emitir.calcularLiquidacionCompra(eq(tenant), any())).thenReturn(c);
        mvc.perform(post("/v1/liquidaciones-compra/calcular").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.liquidacion.neto_pagar").value(985.00))
                .andExpect(jsonPath("$.datos.enlaces").isEmpty());
        verify(emitir).calcularLiquidacionCompra(eq(tenant), any());
    }
}
