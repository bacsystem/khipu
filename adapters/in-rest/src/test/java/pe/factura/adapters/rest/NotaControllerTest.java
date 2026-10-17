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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotaController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class NotaControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EmitirComprobanteUseCase emitir;
    @MockBean pe.factura.application.port.in.ValidarCompradorUseCase comprador;
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

    /** #378: SUNAT aplica a las notas la misma regla del RUC del receptor (anexos de NC y ND): la respuesta avisa 4013 y 4014 igual que la de una factura. */
    @Test void laNotaAvisaLoQueSunatObservaDelRucDelComprador() throws Exception {
        Comprobante nota = notaAceptada(tenant);
        when(emitir.emitirNota(eq(tenant), any())).thenReturn(nota);
        when(comprador.avisosDe(nota.receptor())).thenReturn(List.of(new pe.factura.application.port.in.ValidarCompradorUseCase.Aviso("4014", "El RUC del comprador figura como no habido en SUNAT")));

        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos[0].codigo").value("4014"));
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

    /** #293: el mismo cuerpo sirve para una nota sobre una boleta, con serie B en la nota y en el documento que modifica; el caso de uso decide el resto. */
    @Test void aceptaUnaNotaSobreUnaBoleta() throws Exception {
        when(emitir.emitirNota(eq(tenant), any())).thenReturn(notaAceptada(tenant));
        String sobreBoleta = CUERPO.replace("\"serie\":\"FC01\"", "\"serie\":\"BC01\"").replace("{\"serie\":\"F001\"", "{\"serie\":\"B001\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(sobreBoleta)).andExpect(status().isCreated());
        ArgumentCaptor<EmitirNotaCommand> cap = ArgumentCaptor.forClass(EmitirNotaCommand.class);
        verify(emitir).emitirNota(eq(tenant), cap.capture());
        assertThat(cap.getValue().serie()).isEqualTo("BC01");
        assertThat(cap.getValue().serieAfectada()).isEqualTo("B001");
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
        String serieMal = CUERPO.replace("\"serie\":\"FC01\"", "\"serie\":\"XC01\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(serieMal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['serie']").exists());
        String afectadoMal = CUERPO.replace("{\"serie\":\"F001\"", "{\"serie\":\"T001\"");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(afectadoMal))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['documentoAfectado.serie']").exists());
        String sinAfectado = CUERPO.replace("\"documento_afectado\":{\"serie\":\"F001\",\"numero\":601},", "");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(sinAfectado))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errores['documentoAfectado']").exists());
        verify(emitir, never()).emitirNota(any(), any());
    }

    // --- #355: Idempotency-Key ---------------------------------------------------------------------------------------------------

    static final String CLAVE = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f";

    private org.springframework.test.web.servlet.ResultActions emitirConClave(String clave, String cuerpoJson) throws Exception {
        return mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                .header("Idempotency-Key", clave).content(cuerpoJson));
    }

    private Idempotencia claveRecibida() {
        ArgumentCaptor<Idempotencia> cap = ArgumentCaptor.forClass(Idempotencia.class);
        verify(emitir, org.mockito.Mockito.atLeastOnce()).emitirNota(eq(tenant), any(), cap.capture());
        return cap.getValue();
    }

    @Test void conClaveLaNotaNuevaEs201YLlevaLaClaveYUnaHuellaDelPedido() throws Exception {
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), false));

        emitirConClave(CLAVE, CUERPO).andExpect(status().isCreated()).andExpect(jsonPath("$.datos.numero").value(4));

        Idempotencia idem = claveRecibida();
        assertThat(idem.clave()).isEqualTo(CLAVE);
        assertThat(idem.huella()).matches("[0-9a-f]{64}");
        verify(emitir, never()).emitirNota(any(), any());
    }

    /** Con clave de idempotencia es otro camino del controlador: también avisa (#378). */
    @Test void conClaveDeIdempotenciaLaNotaTambienAvisa() throws Exception {
        Comprobante nota = notaAceptada(tenant);
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(nota, false));
        when(comprador.avisosDe(nota.receptor())).thenReturn(List.of(new pe.factura.application.port.in.ValidarCompradorUseCase.Aviso("4013", "no activo")));

        emitirConClave(CLAVE, CUERPO).andExpect(status().isCreated()).andExpect(jsonPath("$.avisos[0].codigo").value("4013"));
    }

    /** El reintento devuelve la misma nota con 200: no se creó nada nuevo. */
    @Test void unPedidoRepetidoEs200ConLaMismaNota() throws Exception {
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), true));

        emitirConClave(CLAVE, CUERPO).andExpect(status().isOk()).andExpect(jsonPath("$.datos.numero").value(4))
                .andExpect(jsonPath("$.datos.enlaces.xml").value(org.hamcrest.Matchers.startsWith("/v1/facturas/")));
    }

    /** La huella es del pedido interpretado, no de sus bytes; cambiar un dato (aquí el motivo o la serie afectada) da otra. */
    @Test void laHuellaNoDependeDelFormatoDelJsonPeroSiDeSusDatos() throws Exception {
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), false));

        emitirConClave(CLAVE, CUERPO);
        String original = claveRecibida().huella();
        reset(emitir);
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), false));
        emitirConClave(CLAVE, CUERPO.replace("\n", "\n\n  ").replace(":", " : "));
        String reformateado = claveRecibida().huella();
        reset(emitir);
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), false));
        emitirConClave(CLAVE, CUERPO.replace("\"numero\":601", "\"numero\":602"));
        String otroDocumento = claveRecibida().huella();
        reset(emitir);
        when(emitir.emitirNota(eq(tenant), any(), any())).thenReturn(new EmitirComprobanteUseCase.Emision(notaAceptada(tenant), false));
        emitirConClave(CLAVE, CUERPO.replace("\"motivo\":\"01\"", "\"motivo\":\"06\""));
        String otroMotivo = claveRecibida().huella();

        assertThat(reformateado).isEqualTo(original);
        assertThat(otroDocumento).isNotEqualTo(original);
        assertThat(otroMotivo).isNotEqualTo(original).isNotEqualTo(otroDocumento);
    }

    @Test void unaClaveMalFormadaEs422SinEmitir() throws Exception {
        for (String mala : List.of("", "corta", "con espacios aquí", "a".repeat(101), "../../x", "<script>"))
            emitirConClave(mala, CUERPO).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_INVALIDA"));
        verifyNoInteractions(emitir);
    }

    @Test void laMismaClaveConOtraNotaEs422() throws Exception {
        when(emitir.emitirNota(eq(tenant), any(), any())).thenThrow(new DomainException("IDEMPOTENCIA_INVALIDA", "La clave ya se usó con otro contenido"));

        emitirConClave(CLAVE, CUERPO).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_INVALIDA"));
    }

    /** Un cuerpo inválido es 422 de validación antes de mirar la clave: no se reserva nada. */
    @Test void unCuerpoInvalidoConClaveEs422DeValidacionSinLlegarAlCasoDeUso() throws Exception {
        emitirConClave(CLAVE, CUERPO.replace("\"tipo\":\"07\"", "\"tipo\":\"01\"")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verifyNoInteractions(emitir);
    }

    /** H5 (revisión de #354): un elemento nulo de `items` es 422 VALIDACION, no un 500 al mapear al dominio. */
    @Test void unItemNuloEs422DeValidacionYNoUn500() throws Exception {
        String conNulo = CUERPO.replace("\"descripcion\":", "\"items\":[null],\"descripcion\":");
        mvc.perform(post("/v1/notas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(conNulo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores['items[0]']").exists());
        verify(emitir, never()).emitirNota(any(), any());
    }
}
