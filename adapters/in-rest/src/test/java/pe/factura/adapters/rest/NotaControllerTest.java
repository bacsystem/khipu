package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.EmitirNotaCommand;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class NotaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EmitirComprobanteUseCase emitir;
    UUID tenant = UUID.randomUUID();

    static final String CUERPO = """
        {"tipo":"07","serie":"FC01","fecha_emision":"2026-09-18","documento_afectado":{"serie":"F001","numero":601},
         "motivo":"01","descripcion":"Anulación por error en el pedido"}
        """;

    static Comprobante notaAceptada(UUID tenant) {
        Comprobante c = Comprobante.nota(tenant, TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 601, "01", "Anulación por error en el pedido"), new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima")));
        c.asignarNumero(4, "20100066603"); c.firmar("HASH", "k.xml"); c.marcarEnviado();
        c.aplicarCdr(new Cdr("0", "La Nota de Credito numero FC01-4, ha sido aceptada", List.of()), "k.zip");
        return c;
    }

    @Test void emiteUnaNotaTotal() throws Exception {
        when(emitir.emitirNota(eq(tenant), any())).thenReturn(notaAceptada(tenant));
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tipo").value("07"))
                .andExpect(jsonPath("$.datos.serie").value("FC01"))
                .andExpect(jsonPath("$.datos.numero").value(4))
                .andExpect(jsonPath("$.datos.estado_documento").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.nota.documento_afectado").value("F001-601"))
                .andExpect(jsonPath("$.datos.nota.tipo_afectado").value("01"))
                .andExpect(jsonPath("$.datos.nota.motivo").value("01"))
                .andExpect(jsonPath("$.datos.nota.motivo_descripcion").value("Anulación de la operación"))
                .andExpect(jsonPath("$.datos.nota.descripcion").value("Anulación por error en el pedido"))
                .andExpect(jsonPath("$.datos.notas").doesNotExist())
                .andExpect(jsonPath("$.datos.enlaces.xml").value(org.hamcrest.Matchers.startsWith("/v1/facturas/")));
        ArgumentCaptor<EmitirNotaCommand> cap = ArgumentCaptor.forClass(EmitirNotaCommand.class);
        verify(emitir).emitirNota(eq(tenant), cap.capture());
        EmitirNotaCommand cmd = cap.getValue();
        assertThat(cmd.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(cmd.copiaLaFactura()).isTrue();
        assertThat(cmd.serieAfectada()).isEqualTo("F001");
        assertThat(cmd.numeroAfectado()).isEqualTo(601);
        assertThat(cmd.enviarAutomatico()).isTrue();
    }

    @Test void notaParcialConItemsYNotaDeDebito() throws Exception {
        when(emitir.emitirNota(eq(tenant), any())).thenReturn(notaAceptada(tenant));
        String parcial = CUERPO.replace("\"motivo\":\"01\"", "\"motivo\":\"07\"").replace("}\n", ",\"items\":[{\"descripcion\":\"Devuelto\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":59.00,\"tipo_afectacion_igv\":\"10\"}]}\n");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(parcial)).andExpect(status().isCreated());
        String debito = CUERPO.replace("\"tipo\":\"07\"", "\"tipo\":\"08\"").replace("\"serie\":\"FC01\"", "\"serie\":\"FD01\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(debito)).andExpect(status().isCreated());
        ArgumentCaptor<EmitirNotaCommand> cap = ArgumentCaptor.forClass(EmitirNotaCommand.class);
        verify(emitir, org.mockito.Mockito.times(2)).emitirNota(eq(tenant), cap.capture());
        assertThat(cap.getAllValues().get(0).copiaLaFactura()).isFalse();
        assertThat(cap.getAllValues().get(0).items()).hasSize(1);
        assertThat(cap.getAllValues().get(1).tipo()).isEqualTo(TipoDocumento.NOTA_DEBITO);
        assertThat(cap.getAllValues().get(1).enviarAutomatico()).as("sin enviar_automatico en el cuerpo se envía por defecto").isTrue();
    }

    @Test void reglaDeNegocioEs422ConCodigoSunat() throws Exception {
        when(emitir.emitirNota(eq(tenant), any())).thenThrow(new DomainException("NOTA_INVALIDA", "2119 - La factura F001-601 no está aceptada por SUNAT (estado RECHAZADO)"));
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("NOTA_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("2119")));
    }

    @Test void validacionDeFormatoEs422SinLlegarAlCasoDeUso() throws Exception {
        String tipoMal = CUERPO.replace("\"tipo\":\"07\"", "\"tipo\":\"01\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(tipoMal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['tipo']").exists());
        String serieBoleta = CUERPO.replace("\"serie\":\"FC01\"", "\"serie\":\"BC01\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(serieBoleta))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['serie']").exists());
        String sinAfectado = CUERPO.replace("\"documento_afectado\":{\"serie\":\"F001\",\"numero\":601},", "");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(sinAfectado))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['documentoAfectado']").exists());
        verify(emitir, never()).emitirNota(any(), any());
    }
}
