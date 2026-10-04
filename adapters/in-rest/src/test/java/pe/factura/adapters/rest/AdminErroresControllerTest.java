package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.ErrorDeEmision;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Pagina;
import pe.factura.application.port.in.ResolverErroresUseCase;
import pe.factura.application.port.in.ResolverErroresUseCase.Descarte;
import pe.factura.application.port.in.ResolverErroresUseCase.Reintento;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La cola global de errores desde el backoffice (#196). El portal escribe sus tipos a mano a partir de este JSON: lo que se fija acá es su **forma real** (snake_case, lo
 * opcional ausente en lugar de nulo, el total en la cabecera) y cómo responden las acciones cuando el dominio no las permite.
 */
@WebMvcTest(controllers = AdminErroresController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminErroresControllerTest {
    static final UUID COMPROBANTE = UUID.randomUUID();
    static final UUID EMPRESA = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockBean ConsultarColaDeErroresUseCase cola;
    @MockBean ResolverErroresUseCase resolver;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    static ErrorDeEmision error(ClaseDeError clase, FaultSunat fault, Instant proximo, UUID cuenta) {
        return new ErrorDeEmision(COMPROBANTE, EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", cuenta, cuenta == null ? null : "Ana", "20100066603-01-F001-7", "01", "F001", 7,
                LocalDate.of(2026, 10, 12), clase == ClaseDeError.ERROR_DE_ENVIO ? EstadoDocumento.ERROR_ENVIO : EstadoDocumento.RECHAZADO, clase, 3, fault, proximo,
                Instant.parse("2026-10-15T15:00:00Z"), clase.accionable());
    }

    // --- el listado ---------------------------------------------------------------------------------------------------------------------

    @Test void cadaFilaDiceSuEmpresaSuComprobanteSuClaseSuFaultYSusIntentosEnSnakeCase() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(
                error(ClaseDeError.ERROR_DE_ENVIO, new FaultSunat("0109", "El sistema no puede responder"), Instant.parse("2026-10-15T18:30:00Z"), CUENTA)), 57));

        mvc.perform(get("/v1/admin/errores").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "57"))
                .andExpect(jsonPath("$.datos.length()").value(1))
                .andExpect(jsonPath("$.datos[0].comprobante_id").value(COMPROBANTE.toString()))
                .andExpect(jsonPath("$.datos[0].empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos[0].razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos[0].cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos[0].cuenta_nombre").value("Ana"))
                .andExpect(jsonPath("$.datos[0].nombre_archivo").value("20100066603-01-F001-7"))
                .andExpect(jsonPath("$.datos[0].tipo").value("01"))
                .andExpect(jsonPath("$.datos[0].serie").value("F001"))
                .andExpect(jsonPath("$.datos[0].numero").value(7))
                .andExpect(jsonPath("$.datos[0].fecha_emision").value("2026-10-12"))
                .andExpect(jsonPath("$.datos[0].estado").value("ERROR_ENVIO"))
                .andExpect(jsonPath("$.datos[0].clase").value("ERROR_DE_ENVIO"))
                .andExpect(jsonPath("$.datos[0].intentos").value(3))
                .andExpect(jsonPath("$.datos[0].fault.codigo").value("0109"))
                .andExpect(jsonPath("$.datos[0].fault.mensaje").value("El sistema no puede responder"))
                .andExpect(jsonPath("$.datos[0].proximo_intento").value("2026-10-15T18:30:00Z"))
                .andExpect(jsonPath("$.datos[0].actualizado_en").value("2026-10-15T15:00:00Z"))
                .andExpect(jsonPath("$.datos[0].accionable").value(true));
    }

    /** El portal distingue «no hay dato» de «vacío»: lo que no existe no viaja como nulo. */
    @Test void loOpcionalVaAusenteEnLugarDeNulo() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(error(ClaseDeError.ERROR_DE_FORMATO, new FaultSunat(null, "INFRA - x"), null, null)), 1));

        mvc.perform(get("/v1/admin/errores").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].cuenta_id").doesNotExist())
                .andExpect(jsonPath("$.datos[0].cuenta_nombre").doesNotExist())
                .andExpect(jsonPath("$.datos[0].proximo_intento").doesNotExist())
                .andExpect(jsonPath("$.datos[0].fault.codigo").doesNotExist())
                .andExpect(jsonPath("$.datos[0].fault.mensaje").value("INFRA - x"))
                .andExpect(jsonPath("$.datos[0].accionable").value(false));
    }

    @Test void sinFaultElCampoFaltaEnLugarDeSerNulo() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(error(ClaseDeError.FUERA_DE_PLAZO, null, null, CUENTA)), 1));

        mvc.perform(get("/v1/admin/errores").with(clave())).andExpect(jsonPath("$.datos[0].fault").doesNotExist());
    }

    @Test void unaColaVaciaEsUnaListaVaciaConElTotalEnCero() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/errores").with(clave()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.datos.length()").value(0));
    }

    @Test void noTraeMasCamposQueLosQueElPortalConoce() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(error(ClaseDeError.ERROR_DE_ENVIO, new FaultSunat("0109", "x"), Instant.now(), CUENTA)), 1));

        mvc.perform(get("/v1/admin/errores").with(clave()))
                .andExpect(jsonPath("$.datos[0].comprobanteId").doesNotExist())
                .andExpect(jsonPath("$.datos[0].razonSocial").doesNotExist())
                .andExpect(jsonPath("$.datos[0].proximoIntento").doesNotExist())
                .andExpect(jsonPath("$.datos[0].actualizadoEn").doesNotExist())
                .andExpect(jsonPath("$.datos[0].ultimoError").doesNotExist());
    }

    @Test void losFiltrosYLaPaginaLlegamAlCasoDeUso() throws Exception {
        when(cola.listar(any(), eq(2), eq(50))).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/errores").param("clase", "ERROR_DE_FORMATO").param("empresa_id", EMPRESA.toString()).param("q", "andina").param("pagina", "2").param("por_pagina", "50").with(clave()))
                .andExpect(status().isOk());

        verify(cola).listar(new Filtro(ClaseDeError.ERROR_DE_FORMATO, EMPRESA, "andina"), 2, 50);
    }

    @Test void sinFiltrosPideTodoDeLaPrimeraPaginaDeVeinte() throws Exception {
        when(cola.listar(any(), eq(1), eq(20))).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/errores").with(clave())).andExpect(status().isOk());

        verify(cola).listar(new Filtro(null, null, null), 1, 20);
    }

    @Test void laPaginaYElTamanoSeAcotan() throws Exception {
        when(cola.listar(any(), any(Integer.class), any(Integer.class))).thenReturn(new Pagina(List.of(), 0));

        mvc.perform(get("/v1/admin/errores").param("pagina", "0").param("por_pagina", "0").with(clave())).andExpect(status().isOk());
        mvc.perform(get("/v1/admin/errores").param("pagina", "-5").param("por_pagina", "1000").with(clave())).andExpect(status().isOk());

        verify(cola).listar(new Filtro(null, null, null), 1, 1);
        verify(cola).listar(new Filtro(null, null, null), 1, 100);
    }

    @Test void unaClaseOUnaEmpresaMalEscritasSon400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(get("/v1/admin/errores").param("clase", "OTRA").with(clave())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        mvc.perform(get("/v1/admin/errores").param("empresa_id", "no-es-un-uuid").with(clave())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));

        verifyNoInteractions(cola);
    }

    // --- reintentar -------------------------------------------------------------------------------------------------------------------------

    @Test void reintentarDiceComoQuedoElComprobante() throws Exception {
        when(resolver.reintentar(any(ActorAdmin.class), eq(COMPROBANTE))).thenReturn(new Reintento(COMPROBANTE, EstadoDocumento.ERROR_ENVIO, 4, new FaultSunat("0000", "SUNAT respondió HTTP 503")));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.comprobante_id").value(COMPROBANTE.toString()))
                .andExpect(jsonPath("$.datos.estado").value("ERROR_ENVIO"))
                .andExpect(jsonPath("$.datos.intentos").value(4))
                .andExpect(jsonPath("$.datos.fault.codigo").value("0000"))
                .andExpect(jsonPath("$.datos.fault.mensaje").value("SUNAT respondió HTTP 503"));
    }

    @Test void siElReintentoFuncionaNoHayFault() throws Exception {
        when(resolver.reintentar(any(ActorAdmin.class), eq(COMPROBANTE))).thenReturn(new Reintento(COMPROBANTE, EstadoDocumento.ACEPTADO, 2, null));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave()))
                .andExpect(jsonPath("$.datos.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.datos.fault").doesNotExist());
    }

    @Test void reintentarLoHaceElAdministradorAutenticado() throws Exception {
        when(resolver.reintentar(any(ActorAdmin.class), any())).thenReturn(new Reintento(COMPROBANTE, EstadoDocumento.ACEPTADO, 1, null));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave())).andExpect(status().isOk());

        ArgumentCaptor<ActorAdmin> actor = ArgumentCaptor.forClass(ActorAdmin.class);
        verify(resolver).reintentar(actor.capture(), eq(COMPROBANTE));
        assertActorDePlataforma(actor.getValue());
    }

    private static void assertActorDePlataforma(ActorAdmin a) {
        org.assertj.core.api.Assertions.assertThat(a).isEqualTo(AdministradorActual.actor(requestConClave()));
    }

    private static jakarta.servlet.http.HttpServletRequest requestConClave() {
        org.springframework.mock.web.MockHttpServletRequest r = new org.springframework.mock.web.MockHttpServletRequest();
        r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE);
        return r;
    }

    @Test void losErroresDelDominioTienenSuStatusEnElReintento() throws Exception {
        when(resolver.reintentar(any(ActorAdmin.class), eq(COMPROBANTE))).thenThrow(new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave())).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        when(resolver.reintentar(any(ActorAdmin.class), eq(COMPROBANTE))).thenThrow(new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado ACEPTADO"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_NO_ENVIABLE"));

        when(resolver.reintentar(any(ActorAdmin.class), eq(COMPROBANTE))).thenThrow(new DomainException("FUERA_DE_PLAZO", "2108 - fuera de plazo"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/reintento").with(clave())).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"));
    }

    @Test void unIdQueNoEsUnUuidEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/admin/comprobantes/no-es-un-uuid/reintento").with(clave())).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/comprobantes/no-es-un-uuid/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(resolver);
    }

    // --- descartar --------------------------------------------------------------------------------------------------------------------------

    @Test void descartarDiceQueQuedoTerminal() throws Exception {
        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), eq("el cliente lo reemitió"))).thenReturn(new Descarte(COMPROBANTE, EstadoDocumento.DESCARTADO));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"el cliente lo reemitió\"}").with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.comprobante_id").value(COMPROBANTE.toString()))
                .andExpect(jsonPath("$.datos.estado").value("DESCARTADO"));
    }

    @Test void sinCuerpoOSinMotivoElMotivoLlegaNuloYElCasoDeUsoLoRechaza() throws Exception {
        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), eq(null))).thenThrow(new DomainException("MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante"));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{}").with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_REQUERIDO"));
    }

    /** Un descarte sin cuerpo no es un error de forma: es un motivo que falta, y lo dice el caso de uso. */
    @Test void sinCuerpoAlgunoElMotivoLlegaNuloYNoUnaExcepcion() throws Exception {
        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), eq(null))).thenThrow(new DomainException("MOTIVO_REQUERIDO", "Indica por qué se descarta el comprobante"));

        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").with(clave()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_REQUERIDO"));
    }

    @Test void losErroresDelDominioTienenSuStatusEnElDescarte() throws Exception {
        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), any())).thenThrow(new DomainException("ESTADO_NO_DESCARTABLE", "Solo se descarta un comprobante en error de envío"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}").with(clave()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_NO_DESCARTABLE"));

        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), any())).thenThrow(new DomainException("ESTADO_CONFLICTO", "El comprobante cambió de estado en otra transacción"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}").with(clave()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_CONFLICTO"));

        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), any())).thenThrow(new DomainException("MOTIVO_LARGO", "El motivo no puede pasar de 200 caracteres"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}").with(clave()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("MOTIVO_LARGO"));

        when(resolver.descartar(any(ActorAdmin.class), eq(COMPROBANTE), any())).thenThrow(new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}").with(clave())).andExpect(status().isNotFound());
    }

    @Test void unCuerpoQueNoEsJsonEs400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/admin/comprobantes/" + COMPROBANTE + "/descarte").contentType(MediaType.APPLICATION_JSON).content("{no es json").with(clave())).andExpect(status().isBadRequest());

        verifyNoInteractions(resolver);
    }
}
