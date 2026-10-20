package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarGuiaRemisionUseCase;
import pe.factura.application.port.in.EmitirGuiaRemisionCommand;
import pe.factura.application.port.in.EmitirGuiaRemisionUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.GuiaRemisionRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code /v1/guias-remision} (#39): el pedido llega al caso de uso tal cual y la respuesta trae el traslado, el ticket y el CDR. */
@WebMvcTest(controllers = GuiaRemisionController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class GuiaRemisionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EmitirGuiaRemisionUseCase emitir;
    @MockBean EnviarGuiaRemisionUseCase enviar;
    @MockBean ConsultarGuiaRemisionUseCase consultar;
    UUID tenant = UUID.randomUUID();

    static final String REMITENTE = """
        {"serie":"T001","fecha_emision":"2026-10-20","destinatario":{"tipo_doc":"6","num_doc":"20601234565","nombre":"COMERCIAL ANDINA S.A.C."},
         "motivo":"01","modalidad":"02",
         "traslado":{"fecha_inicio":"2026-10-21","peso_bruto":25.5,"unidad_peso":"KGM",
           "partida":{"establecimiento":"0001"},"llegada":{"ubigeo":"040101","direccion":"Calle Mercaderes 200, Arequipa"},
           "vehiculos":[{"placa":"ABC-123"},{"placa":"XYZ987"}],"conductores":[{"tipo_doc":"1","num_doc":"45678912","nombres":"JUAN","apellidos":"QUISPE MAMANI","licencia":"Q45678912"}],
           "indicadores":["retorno_vehiculo_vacio"]},
         "items":[{"codigo":"LAP-15","descripcion":"Laptop 15 pulgadas","unidad":"NIU","cantidad":2,"codigo_sunat":"43211503"}],
         "documentos_relacionados":[{"tipo":"01","numero":"F001-123","ruc_emisor":"20100066603"}],"observaciones":"Frágil","enviar_automatico":false}
        """;

    static final String TRANSPORTISTA = """
        {"serie":"V001","destinatario":{"tipo_doc":"6","num_doc":"20601234565","nombre":"COMERCIAL ANDINA S.A.C."},
         "remitente":{"tipo_doc":"6","num_doc":"20555555556","nombre":"DISTRIBUIDORA NORTE S.A."},"registro_mtc":"1512345CNG",
         "traslado":{"fecha_inicio":"2026-10-21","peso_bruto":100,
           "partida":{"ubigeo":"150101","direccion":"Av. Argentina 1234"},"llegada":{"ubigeo":"040101","direccion":"Calle Mercaderes 200"},
           "vehiculos":[{"placa":"ABC123"}],"conductores":[{"tipo_doc":"1","num_doc":"45678912","nombres":"JUAN","apellidos":"QUISPE","licencia":"Q45678912"}]},
         "items":[{"descripcion":"Cajas","unidad":"BX","cantidad":10}],
         "documentos_relacionados":[{"tipo":"09","numero":"T001-15","ruc_emisor":"20555555556"}]}
        """;

    static GuiaRemision aceptada(UUID tenant) {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-20T15:04:05Z"), ZoneId.of("America/Lima"));
        GuiaRemision g = GuiaRemision.crear(tenant, "20100066603", "T001", LocalDate.of(2026, 10, 20), new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null),
                new Traslado(LocalDate.of(2026, 10, 21), new BigDecimal("25.5"), "KGM", null, new PuntoDeTraslado("070101", "Av. Gambetta 500", "0001", null),
                        PuntoDeTraslado.de("040101", "Calle Mercaderes 200, Arequipa"), List.of(new Vehiculo("ABC123"), new Vehiculo("XYZ987")),
                        List.of(new Conductor("1", "45678912", "JUAN", "QUISPE MAMANI", "Q45678912")), Set.of(IndicadorDeTraslado.RETORNO_VEHICULO_VACIO)),
                List.of(new ItemDeGuia("LAP-15", "Laptop 15 pulgadas", "NIU", new BigDecimal("2"), "43211503")), List.of(new DocumentoDeTraslado("01", "F001-123", "20100066603")), "Frágil", reloj);
        g.asignarNumero(15, "20100066603");
        g.firmar("HASH", "k.xml");
        g.marcarEnviada("7f7b00ab-4fee-4ae2-9e04-fbd16ca18677", LocalDateTime.of(2026, 10, 20, 10, 4, 7));
        g.aplicarCdr(new Cdr("0", "La Guia numero T001-15, ha sido aceptada", List.of()), "r.zip", "https://e-factura.sunat.gob.pe/qr?x=1");
        return g;
    }

    @Test void emiteLaRemitenteYRespondeElTrasladoYElCdr() throws Exception {
        when(emitir.emitir(eq(tenant), any(), isNull())).thenReturn(new EmitirGuiaRemisionUseCase.Emision(aceptada(tenant), false));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(REMITENTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tipo").value("09"))
                .andExpect(jsonPath("$.datos.serie").value("T001"))
                .andExpect(jsonPath("$.datos.numero").value(15))
                .andExpect(jsonPath("$.datos.nombre_archivo").value("20100066603-09-T001-15"))
                .andExpect(jsonPath("$.datos.hora_emision").value("10:04:05"))
                .andExpect(jsonPath("$.datos.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.destinatario.num_doc").value("20601234565"))
                .andExpect(jsonPath("$.datos.motivo").value("01"))
                .andExpect(jsonPath("$.datos.descripcion_motivo").value("Venta"))
                .andExpect(jsonPath("$.datos.modalidad").value("02"))
                .andExpect(jsonPath("$.datos.remitente").doesNotExist())
                .andExpect(jsonPath("$.datos.registro_mtc").doesNotExist())
                .andExpect(jsonPath("$.datos.transportista").doesNotExist())
                .andExpect(jsonPath("$.datos.traslado.fecha_inicio").value("2026-10-21"))
                .andExpect(jsonPath("$.datos.traslado.peso_bruto").value(25.5))
                .andExpect(jsonPath("$.datos.traslado.partida.establecimiento").value("0001"))
                .andExpect(jsonPath("$.datos.traslado.partida.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.traslado.llegada.establecimiento").doesNotExist())
                .andExpect(jsonPath("$.datos.traslado.vehiculos[0].placa").value("ABC123"))
                .andExpect(jsonPath("$.datos.traslado.vehiculos[0].principal").value(true))
                .andExpect(jsonPath("$.datos.traslado.vehiculos[1].principal").value(false))
                .andExpect(jsonPath("$.datos.traslado.conductores[0].licencia").value("Q45678912"))
                .andExpect(jsonPath("$.datos.traslado.indicadores[0]").value("retorno_vehiculo_vacio"))
                .andExpect(jsonPath("$.datos.items[0].codigo_sunat").value("43211503"))
                .andExpect(jsonPath("$.datos.documentos_relacionados[0].descripcion").value("Factura"))
                .andExpect(jsonPath("$.datos.ticket").value("7f7b00ab-4fee-4ae2-9e04-fbd16ca18677"))
                .andExpect(jsonPath("$.datos.fecha_recepcion").value("2026-10-20T10:04:07"))
                .andExpect(jsonPath("$.datos.reintento_automatico").value(false))
                .andExpect(jsonPath("$.datos.cdr.codigo").value("0"))
                .andExpect(jsonPath("$.datos.enlace_consulta").value("https://e-factura.sunat.gob.pe/qr?x=1"))
                .andExpect(jsonPath("$.datos.enlaces.pdf").value(startsWith("/v1/guias-remision/")))
                .andExpect(jsonPath("$.datos.enlaces.cdr").value(startsWith("/v1/guias-remision/")));
        ArgumentCaptor<EmitirGuiaRemisionCommand> cap = ArgumentCaptor.forClass(EmitirGuiaRemisionCommand.class);
        verify(emitir).emitir(eq(tenant), cap.capture(), isNull());
        EmitirGuiaRemisionCommand cmd = cap.getValue();
        assertThat(cmd.datos()).isEqualTo(new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null));
        assertThat(cmd.traslado().partida()).isEqualTo(new PuntoDeTraslado(null, null, "0001", null));
        assertThat(cmd.traslado().vehiculos()).extracting(Vehiculo::placa).containsExactly("ABC123", "XYZ987");
        assertThat(cmd.traslado().indicadores()).containsExactly(IndicadorDeTraslado.RETORNO_VEHICULO_VACIO);
        assertThat(cmd.documentos()).containsExactly(new DocumentoDeTraslado("01", "F001-123", "20100066603"));
        assertThat(cmd.enviarAutomatico()).isFalse();
        assertThat(cmd.observaciones()).isEqualTo("Frágil");
    }

    @Test void laTransportistaLlevaSusDatosYSeEnviaPorDefecto() throws Exception {
        when(emitir.emitir(eq(tenant), any(), isNull())).thenReturn(new EmitirGuiaRemisionUseCase.Emision(aceptada(tenant), false));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(TRANSPORTISTA)).andExpect(status().isCreated());
        ArgumentCaptor<EmitirGuiaRemisionCommand> cap = ArgumentCaptor.forClass(EmitirGuiaRemisionCommand.class);
        verify(emitir).emitir(eq(tenant), cap.capture(), isNull());
        assertThat(cap.getValue().datos()).isEqualTo(new DatosDeGuia.DeTransportista(new ParteDeGuia("6", "20555555556", "DISTRIBUIDORA NORTE S.A."), "1512345CNG"));
        assertThat(cap.getValue().enviarAutomatico()).isTrue();
        assertThat(cap.getValue().traslado().unidadPeso()).isEqualTo("KGM");
    }

    @Test void losDatosDeUnTipoNoVanEnElOtro() throws Exception {
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(TRANSPORTISTA.replace("\"registro_mtc\":\"1512345CNG\",", "\"registro_mtc\":\"1512345CNG\",\"motivo\":\"01\",")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("GUIA_INVALIDA"));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(REMITENTE.replace("\"modalidad\":\"02\",", "\"modalidad\":\"02\",\"registro_mtc\":\"X\",")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("GUIA_INVALIDA"));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(REMITENTE.replace("\"indicadores\":[\"retorno_vehiculo_vacio\"]", "\"indicadores\":[\"volar\"]")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TRASLADO_INVALIDO"));
        verifyNoInteractions(emitir);
    }

    @Test void validaElFormatoAntesDeLlegarAlCasoDeUso() throws Exception {
        for (String[] caso : new String[][]{
                {"\"serie\":\"T001\"", "\"serie\":\"F001\"", "serie"},
                {"\"modalidad\":\"02\"", "\"modalidad\":\"03\"", "modalidad"},
                {"\"unidad_peso\":\"KGM\"", "\"unidad_peso\":\"LTR\"", "traslado.unidadPeso"},
                {"\"items\":[{", "\"items_\":[{", "items"},
                {"\"destinatario\":{\"tipo_doc\":\"6\",\"num_doc\":\"20601234565\",\"nombre\":\"COMERCIAL ANDINA S.A.C.\"}", "\"destinatario\":{\"tipo_doc\":\"6\",\"num_doc\":\"20601234565\"}", "destinatario.nombre"}}) {
            mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(REMITENTE.replace(caso[0], caso[1])))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores['" + caso[2] + "']").exists());
        }
        verifyNoInteractions(emitir);
    }

    @Test void unaReglaDeDominioRespondeSuCodigo() throws Exception {
        when(emitir.emitir(eq(tenant), any(), isNull())).thenThrow(new DomainException("TRANSPORTE_INVALIDO", "2533 - En transporte privado indique el vehículo"));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(REMITENTE))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TRANSPORTE_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value(startsWith("2533")));
    }

    @Test void conIdempotencyKeyUnReintentoEs200() throws Exception {
        when(emitir.emitir(eq(tenant), any(), any())).thenReturn(new EmitirGuiaRemisionUseCase.Emision(aceptada(tenant), true));
        mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .header("Idempotency-Key", "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f").content(REMITENTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.numero").value(15));
        ArgumentCaptor<Idempotencia> cap = ArgumentCaptor.forClass(Idempotencia.class);
        verify(emitir).emitir(eq(tenant), any(), cap.capture());
        assertThat(cap.getValue().clave()).isEqualTo("5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
    }

    @Test void validarNoEmiteYNoTraeEnlaces() throws Exception {
        GuiaRemision g = GuiaRemision.crear(tenant, "20100066603", "T001", LocalDate.of(2026, 10, 20), new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null), aceptada(tenant).traslado(), aceptada(tenant).items(), List.of(), null,
                Clock.fixed(Instant.parse("2026-10-20T15:04:05Z"), ZoneId.of("America/Lima")));
        when(emitir.validar(eq(tenant), any())).thenReturn(g);
        mvc.perform(post("/v1/guias-remision/validar").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(REMITENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.numero").doesNotExist())
                .andExpect(jsonPath("$.datos.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.datos.enlaces").doesNotExist());
        verify(emitir, never()).emitir(any(), any(), any());
    }

    @Test void listaConFiltrosYTotal() throws Exception {
        when(consultar.listar(eq(tenant), any(), eq(1), eq(20))).thenReturn(List.of(aceptada(tenant)));
        when(consultar.contar(eq(tenant), any())).thenReturn(1L);
        mvc.perform(get("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).param("tipo", "09").param("estado", "ACEPTADO").param("serie", "T001"))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$.datos[0].serie").value("T001"));
        ArgumentCaptor<GuiaRemisionRepository.Filtro> cap = ArgumentCaptor.forClass(GuiaRemisionRepository.Filtro.class);
        verify(consultar).listar(eq(tenant), cap.capture(), eq(1), eq(20));
        assertThat(cap.getValue()).isEqualTo(new GuiaRemisionRepository.Filtro(EstadoGuia.ACEPTADO, TipoDocumento.GUIA_REMITENTE, "T001", null, null));
        mvc.perform(get("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).param("tipo", "01")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).param("serie", "F001")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).param("desde", "2026-10-21").param("hasta", "2026-10-20")).andExpect(status().isBadRequest());
    }

    @Test void enviarTicketYDescargas() throws Exception {
        GuiaRemision g = aceptada(tenant);
        when(enviar.enviar(tenant, g.id())).thenReturn(g);
        when(enviar.consultarTicket(tenant, g.id())).thenThrow(new DomainException("TICKET_NO_PENDIENTE", "ya aceptada"));
        when(consultar.obtener(tenant, g.id())).thenReturn(g);
        when(consultar.xml(tenant, g.id())).thenReturn("<DespatchAdvice/>".getBytes());
        when(consultar.cdr(tenant, g.id())).thenReturn(new byte[]{1});
        when(consultar.pdf(tenant, g.id())).thenReturn("%PDF".getBytes());
        mvc.perform(post("/v1/guias-remision/{id}/enviar", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isOk()).andExpect(jsonPath("$.datos.estado").value("ACEPTADO"));
        mvc.perform(post("/v1/guias-remision/{id}/ticket", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TICKET_NO_PENDIENTE"));
        mvc.perform(get("/v1/guias-remision/{id}/xml", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20100066603-09-T001-15.xml\""));
        mvc.perform(get("/v1/guias-remision/{id}/pdf", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/v1/guias-remision/{id}/cdr", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"R-20100066603-09-T001-15.zip\""));
        mvc.perform(get("/v1/guias-remision/{id}/cdr", g.id()).requestAttr(TenantActual.ATRIBUTO, tenant).param("formato", "pdf")).andExpect(status().isBadRequest());
        when(consultar.obtener(tenant, UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenThrow(new DomainException("NO_ENCONTRADO", "Guía de remisión no encontrada"));
        mvc.perform(get("/v1/guias-remision/{id}", "00000000-0000-0000-0000-000000000001").requestAttr(TenantActual.ATRIBUTO, tenant)).andExpect(status().isNotFound());
    }
}
