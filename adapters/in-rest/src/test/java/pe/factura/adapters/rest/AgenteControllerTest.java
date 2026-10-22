package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.EmitirAgenteCommand;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
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

/** {@code POST /v1/retenciones} y {@code POST /v1/percepciones} (#40): el pedido llega al caso de uso con su tipo y la respuesta trae el bloque {@code agente}. */
@WebMvcTest(controllers = AgenteController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AgenteControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EmitirComprobanteUseCase emitir;
    UUID tenant = UUID.randomUUID();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static final String RETENCION = """
        {"serie":"R001","fecha_emision":"2026-09-13",
         "proveedor":{"ruc":"20131312955","razon_social":"PROVEEDOR DEL NORTE S.A.C.","direccion":"Av. Grau 123"},
         "regimen":"01",
         "documentos":[{"tipo":"01","numero":"F001-123","fecha_emision":"2026-09-01","importe_total":1180.00,"moneda":"PEN","pago":{"numero":1,"fecha":"2026-09-12","importe":1180.00}},
                       {"tipo":"01","numero":"E001-45","fecha_emision":"2026-08-20","importe_total":1000.00,"moneda":"USD","pago":{"fecha":"2026-09-10","importe":500.00},
                        "tipo_cambio":{"factor":3.756}}],
         "enviar_automatico":false,"observaciones":"Pago de septiembre"}
        """;

    static final String PERCEPCION = """
        {"serie":"P001","fecha_emision":"2026-09-13",
         "cliente":{"tipo_doc":"1","num_doc":"45678912","nombre":"JUAN PEREZ"},
         "regimen":"03","tasa":0.5,
         "documentos":[{"tipo":"03","numero":"B001-15","fecha_emision":"2026-09-02","importe_total":590.00,"pago":{"fecha":"2026-09-13","importe":590.00}}]}
        """;

    static Comprobante retencionAceptada(UUID tenant) {
        Comprobante c = Comprobante.deAgente(tenant, "R001", LocalDate.of(2026, 9, 13), new Receptor("6", "20131312955", "PROVEEDOR DEL NORTE S.A.C.", "Av. Grau 123"),
                new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(
                        new ComprobanteDeAgente.Documento("01", "F001-123", LocalDate.of(2026, 9, 1), new BigDecimal("1180.00"), "PEN", 1, LocalDate.of(2026, 9, 12), new BigDecimal("1180.00"), null, null),
                        new ComprobanteDeAgente.Documento("01", "E001-45", LocalDate.of(2026, 8, 20), new BigDecimal("1000.00"), "USD", 1, LocalDate.of(2026, 9, 10), new BigDecimal("500.00"),
                                new BigDecimal("3.756"), null)))).crear("20100066603", CLOCK);
        c.asignarNumero(7, "20100066603"); c.firmar("HASH", "k.xml"); c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "El Comprobante numero R001-7 ha sido aceptado", List.of()), "k.zip");
        return c;
    }

    @Test void emiteLaRetencionYRespondeElBloqueAgente() throws Exception {
        when(emitir.emitirDeAgente(eq(tenant), any())).thenReturn(retencionAceptada(tenant));
        mvc.perform(post("/v1/retenciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(RETENCION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tipo").value("20"))
                .andExpect(jsonPath("$.datos.nombre_archivo").value("20100066603-20-R001-7"))
                .andExpect(jsonPath("$.datos.tipo_operacion").doesNotExist())
                .andExpect(jsonPath("$.datos.moneda").value("PEN"))
                .andExpect(jsonPath("$.datos.items").isEmpty())
                .andExpect(jsonPath("$.datos.receptor.num_doc").value("20131312955"))
                .andExpect(jsonPath("$.datos.agente.regimen").value("01"))
                .andExpect(jsonPath("$.datos.agente.descripcion_regimen").value("Tasa 3%"))
                .andExpect(jsonPath("$.datos.agente.tasa").value(3))
                .andExpect(jsonPath("$.datos.agente.importe_total").value(91.74))
                .andExpect(jsonPath("$.datos.agente.importe_neto").value(2966.26))
                .andExpect(jsonPath("$.datos.agente.documentos[0].importe_agente").value(35.40))
                .andExpect(jsonPath("$.datos.agente.documentos[0].neto").value(1144.60))
                .andExpect(jsonPath("$.datos.agente.documentos[0].tipo_cambio").doesNotExist())
                .andExpect(jsonPath("$.datos.agente.documentos[1].moneda").value("USD"))
                .andExpect(jsonPath("$.datos.agente.documentos[1].pago.importe").value(500.00))
                .andExpect(jsonPath("$.datos.agente.documentos[1].tipo_cambio.factor").value(3.756))
                .andExpect(jsonPath("$.datos.agente.documentos[1].tipo_cambio.fecha").value("2026-09-10"))
                .andExpect(jsonPath("$.datos.agente.documentos[1].importe_agente").value(56.34))
                .andExpect(jsonPath("$.datos.totales.total").value(91.74))
                .andExpect(jsonPath("$.datos.liquidacion").doesNotExist())
                .andExpect(jsonPath("$.datos.fecha_limite_envio").value("2026-09-20"))
                .andExpect(jsonPath("$.datos.enlaces.pdf").value(org.hamcrest.Matchers.startsWith("/v1/facturas/")));
        ArgumentCaptor<EmitirAgenteCommand> cap = ArgumentCaptor.forClass(EmitirAgenteCommand.class);
        verify(emitir).emitirDeAgente(eq(tenant), cap.capture());
        EmitirAgenteCommand cmd = cap.getValue();
        assertThat(cmd.datos().tipo()).isEqualTo(TipoDocumento.RETENCION);
        assertThat(cmd.contraparte()).isEqualTo(new Receptor("6", "20131312955", "PROVEEDOR DEL NORTE S.A.C.", "Av. Grau 123"));
        assertThat(cmd.datos().tasa()).as("la del régimen").isEqualByComparingTo("3");
        assertThat(cmd.datos().documentos()).hasSize(2);
        assertThat(cmd.datos().documentos().get(1).numeroPago()).as("por defecto 1").isEqualTo(1);
        assertThat(cmd.datos().documentos().get(1).fechaTipoCambio()).as("por defecto la del pago").isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(cmd.enviarAutomatico()).isFalse();
        assertThat(cmd.observaciones()).isEqualTo("Pago de septiembre");
    }

    @Test void laPercepcionLlegaConSuClienteYSuRegimen() throws Exception {
        when(emitir.emitirDeAgente(eq(tenant), any())).thenReturn(retencionAceptada(tenant));
        mvc.perform(post("/v1/percepciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(PERCEPCION)).andExpect(status().isCreated());
        ArgumentCaptor<EmitirAgenteCommand> cap = ArgumentCaptor.forClass(EmitirAgenteCommand.class);
        verify(emitir).emitirDeAgente(eq(tenant), cap.capture());
        EmitirAgenteCommand cmd = cap.getValue();
        assertThat(cmd.datos().tipo()).isEqualTo(TipoDocumento.PERCEPCION);
        assertThat(cmd.contraparte()).isEqualTo(new Receptor("1", "45678912", "JUAN PEREZ", null));
        assertThat(cmd.datos().regimen()).isEqualTo("03");
        assertThat(cmd.datos().tasa()).isEqualByComparingTo("0.5");
        assertThat(cmd.datos().documentos().get(0).moneda()).as("por defecto soles").isEqualTo("PEN");
        assertThat(cmd.enviarAutomatico()).isTrue();
    }

    @Test void validaElFormatoAntesDeLlegarAlCasoDeUso() throws Exception {
        for (String[] caso : new String[][]{
                {"\"serie\":\"R001\"", "\"serie\":\"P001\"", "serie"},
                {"\"ruc\":\"20131312955\"", "\"ruc\":\"2013131295\"", "proveedor.ruc"},
                {"\"regimen\":\"01\"", "\"regimen\":\"1\"", "regimen"},
                {"\"importe_total\":1180.00", "\"importe_total\":-1", "documentos[0].importeTotal"},
                {"\"pago\":{\"numero\":1,\"fecha\":\"2026-09-12\",\"importe\":1180.00}", "\"pago\":{\"numero\":1,\"importe\":1180.00}", "documentos[0].pago.fecha"},
                {"\"tipo_cambio\":{\"factor\":3.756}", "\"tipo_cambio\":{\"factor\":3.7561234}", "documentos[1].tipoCambio.factor"}}) {
            mvc.perform(post("/v1/retenciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(RETENCION.replace(caso[0], caso[1])))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores['" + caso[2] + "']").exists());
        }
        mvc.perform(post("/v1/percepciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(PERCEPCION.replace("\"serie\":\"P001\"", "\"serie\":\"R001\"")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errores['serie']").exists());
        verifyNoInteractions(emitir);
    }

    @Test void sinLaDesignacionRespondeSuCodigo() throws Exception {
        when(emitir.emitirDeAgente(eq(tenant), any())).thenThrow(new DomainException("AGENTE_NO_DESIGNADO", "2617 - La empresa no está registrada como agente de retención"));
        mvc.perform(post("/v1/retenciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(RETENCION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("AGENTE_NO_DESIGNADO"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("2617")));
    }

    @Test void conIdempotencyKeyUnReintentoEs200YCalcularNoEmite() throws Exception {
        when(emitir.emitirDeAgente(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(retencionAceptada(tenant), true));
        mvc.perform(post("/v1/retenciones").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .header("Idempotency-Key", "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f").content(RETENCION))
                .andExpect(status().isOk());
        ArgumentCaptor<Idempotencia> cap = ArgumentCaptor.forClass(Idempotencia.class);
        verify(emitir).emitirDeAgente(eq(tenant), any(), cap.capture());
        assertThat(cap.getValue().clave()).isEqualTo("5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f");

        when(emitir.calcularDeAgente(eq(tenant), any())).thenReturn(retencionAceptada(tenant));
        mvc.perform(post("/v1/percepciones/calcular").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(PERCEPCION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.enlaces").isEmpty());
        mvc.perform(post("/v1/retenciones/calcular").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(RETENCION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.agente.importe_total").value(91.74));
    }
}
