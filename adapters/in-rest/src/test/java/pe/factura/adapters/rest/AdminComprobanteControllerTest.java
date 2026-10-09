package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.Ficha;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase.RespuestaSunat;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La ficha de un comprobante en el backoffice (#251). El portal escribe sus tipos a mano: se fija la forma real del JSON (snake_case, lo opcional ausente). */
@WebMvcTest(controllers = AdminComprobanteController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminComprobanteControllerTest {
    static final UUID COMPROBANTE = UUID.randomUUID();
    static final UUID EMPRESA = UUID.randomUUID();
    static final UUID CUENTA = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockBean ConsultarComprobanteAdminUseCase consulta;

    static org.springframework.test.web.servlet.request.RequestPostProcessor clave() {
        return r -> { r.setAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE); return r; };
    }

    @Test void laFichaDiceEmpresaIdentidadEstadoIntentosRespuestaDeSunatYArchivosSinSuContenido() throws Exception {
        when(consulta.ficha(COMPROBANTE)).thenReturn(new Ficha(COMPROBANTE, EMPRESA, "20100066603", "COMERCIAL ANDINA SAC", CUENTA, "20100066603-01-F001-7", "01", "F001", 7L,
                LocalDate.of(2026, 10, 12), EstadoDocumento.RECHAZADO, 2, "0109 - El sistema no puede responder", new RespuestaSunat("2324", "registrado previamente"), true, true));

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
                LocalDate.of(2026, 10, 12), EstadoDocumento.FIRMADO, 0, null, null, true, false));

        mvc.perform(get("/v1/admin/comprobantes/" + COMPROBANTE).with(clave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cuenta_id").doesNotExist())
                .andExpect(jsonPath("$.datos.ultimo_error").doesNotExist())
                .andExpect(jsonPath("$.datos.respuesta_sunat").doesNotExist())
                .andExpect(jsonPath("$.datos.tiene_cdr").value(false));
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
