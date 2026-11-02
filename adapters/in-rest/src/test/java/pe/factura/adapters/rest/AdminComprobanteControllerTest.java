package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.BajaDeFicha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.Ficha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.RespuestaSunat;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La ficha de un comprobante en el backoffice (#251). El portal escribe sus tipos a mano: se fija la forma real del JSON (snake_case, lo opcional ausente). */
@WebMvcTest(controllers = AdminComprobanteController.class)
@Import(GlobalExceptionHandler.class)
class AdminComprobanteControllerTest {
    static final UUID COMPROBANTE = UUID.randomUUID();
    static final UUID EMPRESA = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarComprobanteAdminUseCase consulta;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    @Test void laFichaDiceEmpresaIdentidadEstadoIntentosRespuestaDeSunatYArchivosSinSuContenido() throws Exception {
        when(consulta.ficha(COMPROBANTE)).thenReturn(new Ficha(COMPROBANTE, EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "20100066603-01-F001-7", "01", "F001", 7L,
                LocalDate.of(2026, 10, 12), EstadoDocumento.RECHAZADO, 2, "0109 - El sistema no puede responder", new RespuestaSunat("2324", "registrado previamente"), true, true, List.of()));

        mvc.perform(get("/v1/admin/comprobantes/" + COMPROBANTE).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.id").value(COMPROBANTE.toString()))
                .andExpect(jsonPath("$.datos.empresa_id").value(EMPRESA.toString()))
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos.razon_social").value("COMERCIAL ANDINA SAC"))
                .andExpect(jsonPath("$.datos.cuenta_id").value(CUENTA.toString()))
                .andExpect(jsonPath("$.datos.nombre_archivo").value("20100066603-01-F001-7"))
                .andExpect(jsonPath("$.datos.tipo").value("01"))
                .andExpect(jsonPath("$.datos.serie").value("F001"))
                .andExpect(jsonPath("$.datos.numero").value(7))
                .andExpect(jsonPath("$.datos.fecha_emision").value("2026-10-12"))
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.datos.intentos").value(2))
                .andExpect(jsonPath("$.datos.ultimo_error").value("0109 - El sistema no puede responder"))
                .andExpect(jsonPath("$.datos.respuesta_sunat.codigo").value("2324"))
                .andExpect(jsonPath("$.datos.respuesta_sunat.descripcion").value("registrado previamente"))
                .andExpect(jsonPath("$.datos.tiene_xml").value(true))
                .andExpect(jsonPath("$.datos.tiene_cdr").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("xml_key"))));
    }

    @Test void loQueNoHayQuedaAusente() throws Exception {
        when(consulta.ficha(COMPROBANTE)).thenReturn(new Ficha(COMPROBANTE, EMPRESA, "20100066603", "INTEGRADOR SAC", null, "20100066603-01-F001-8", "01", "F001", 8L,
                LocalDate.of(2026, 10, 12), EstadoDocumento.FIRMADO, 0, null, null, true, false, List.of()));

        mvc.perform(get("/v1/admin/comprobantes/" + COMPROBANTE).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").doesNotExist())
                .andExpect(jsonPath("$.datos.ultimo_error").doesNotExist())
                .andExpect(jsonPath("$.datos.respuesta_sunat").doesNotExist())
                .andExpect(jsonPath("$.datos.tiene_cdr").value(false))
                .andExpect(jsonPath("$.datos.bajas").isArray())
                .andExpect(jsonPath("$.datos.bajas").isEmpty());
    }

    /** #362 H9: la baja o el RC del comprobante viaja en snake_case, con lo que no hay (ticket, CDR, último error) ausente, y la lista de la más reciente a la más antigua. */
    @Test void lasBajasDelComprobanteViajanConSuEstadoTicketMotivoYCdr() throws Exception {
        UUID rc = UUID.randomUUID();
        UUID alta = UUID.randomUUID();
        when(consulta.ficha(COMPROBANTE)).thenReturn(new Ficha(COMPROBANTE, EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "20100066603-03-B001-7", "03", "B001", 7L,
                LocalDate.of(2026, 10, 12), EstadoDocumento.ANULADO, 0, null, new RespuestaSunat("0", "La Boleta numero B001-7, ha sido aceptada"), true, true, List.of(
                new BajaDeFicha(rc, "RC-20261013-2", "BAJA", "ACEPTADA", "1789768174685", "Se cobró dos veces", LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 12), false,
                        new RespuestaSunat("0", "El Resumen diario RC-20261013-2, ha sido aceptado"), 0, null),
                new BajaDeFicha(alta, "RC-20261012-1", "ALTA", "ERROR_ENVIO", null, "Pasó el envío individual: se informa en el resumen diario", LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 12), true, null, 2, "SUNAT respondió HTTP 503"))));

        mvc.perform(get("/v1/admin/comprobantes/" + COMPROBANTE).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.bajas.length()").value(2))
                .andExpect(jsonPath("$.datos.bajas[0].id").value(rc.toString()))
                .andExpect(jsonPath("$.datos.bajas[0].identificador").value("RC-20261013-2"))
                .andExpect(jsonPath("$.datos.bajas[0].condicion").value("BAJA"))
                .andExpect(jsonPath("$.datos.bajas[0].estado").value("ACEPTADA"))
                .andExpect(jsonPath("$.datos.bajas[0].ticket").value("1789768174685"))
                .andExpect(jsonPath("$.datos.bajas[0].motivo").value("Se cobró dos veces"))
                .andExpect(jsonPath("$.datos.bajas[0].fecha_generacion").value("2026-10-13"))
                .andExpect(jsonPath("$.datos.bajas[0].fecha_referencia").value("2026-10-12"))
                .andExpect(jsonPath("$.datos.bajas[0].fuera_de_plazo").value(false))
                .andExpect(jsonPath("$.datos.bajas[0].cdr.codigo").value("0"))
                .andExpect(jsonPath("$.datos.bajas[0].cdr.descripcion").value("El Resumen diario RC-20261013-2, ha sido aceptado"))
                .andExpect(jsonPath("$.datos.bajas[0].intentos").value(0))
                .andExpect(jsonPath("$.datos.bajas[0].ultimo_error").doesNotExist())
                .andExpect(jsonPath("$.datos.bajas[1].identificador").value("RC-20261012-1"))
                .andExpect(jsonPath("$.datos.bajas[1].condicion").value("ALTA"))
                .andExpect(jsonPath("$.datos.bajas[1].ticket").doesNotExist())
                .andExpect(jsonPath("$.datos.bajas[1].cdr").doesNotExist())
                .andExpect(jsonPath("$.datos.bajas[1].fuera_de_plazo").value(true))
                .andExpect(jsonPath("$.datos.bajas[1].intentos").value(2))
                .andExpect(jsonPath("$.datos.bajas[1].ultimo_error").value("SUNAT respondió HTTP 503"));
    }

    @Test void unComprobanteQueNoExisteEs404() throws Exception {
        when(consulta.ficha(COMPROBANTE)).thenThrow(new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        mvc.perform(get("/v1/admin/comprobantes/" + COMPROBANTE).with(clave())).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void unIdQueNoEsUuidEs400SinConsultar() throws Exception {
        mvc.perform(get("/v1/admin/comprobantes/no-es-un-uuid").with(clave())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        verifyNoInteractions(consulta);
    }
}
