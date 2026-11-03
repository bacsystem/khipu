package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.ConsultarPadronRucUseCase;
import pe.factura.application.port.in.ConsultarPadronRucUseCase.Solicitante;
import pe.factura.application.port.out.PadronRucRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #575: el alta asistida consulta el padrón sin empresa; el cupo es del administrador. */
@WebMvcTest(controllers = AdminConsultaController.class)
@Import(GlobalExceptionHandler.class)
class AdminConsultaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConsultarPadronRucUseCase padron;

    final ConsultarPadronRucUseCase.Consulta consulta = new ConsultarPadronRucUseCase.Consulta(
            new PadronRucRepository.Contribuyente("20100066603", "TIENDAS S.A.C.", "ACTIVO", "HABIDO", "150101", "AV. PERU NRO. 1"), Instant.parse("2026-10-15T09:40:00Z"));

    @Test void unAdministradorConsultaConSuPropioCupo() throws Exception {
        UUID admin = UUID.randomUUID();
        when(padron.consultar(any(), any())).thenReturn(consulta);

        mvc.perform(get("/v1/admin/consultas/ruc/20100066603").requestAttr(AdministradorActual.ATRIBUTO, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.razon_social").value("TIENDAS S.A.C."))
                .andExpect(jsonPath("$.datos.activo").value(true))
                .andExpect(jsonPath("$.datos.direccion").value("AV. PERU NRO. 1"));
        verify(padron).consultar(Solicitante.backoffice(ActorAdmin.administrador(admin, "127.0.0.1")), "20100066603");
    }

    @Test void conLaClaveDePlataformaComparteUnSoloCupo() throws Exception {
        when(padron.consultar(any(), any())).thenReturn(consulta);

        mvc.perform(get("/v1/admin/consultas/ruc/20100066603").requestAttr(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA, Boolean.TRUE))
                .andExpect(status().isOk());
        verify(padron).consultar(new Solicitante("admin:plataforma"), "20100066603");
    }

    /** Sin credencial validada por el filtro no se inventa un solicitante: ni se consulta. */
    @Test void sinAdministradorNoConsulta() throws Exception {
        mvc.perform(get("/v1/admin/consultas/ruc/20100066603")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
        verifyNoInteractions(padron);
    }

    @Test void losErroresDelPadronTienenElMismoCodigoHttpQueEnElPortal() throws Exception {
        record Caso(String codigo, int http) { }
        for (Caso c : new Caso[]{new Caso("NO_ENCONTRADO", 404), new Caso("PARAMETRO_INVALIDO", 400), new Caso("PADRON_NO_CARGADO", 503), new Caso("DEMASIADAS_CONSULTAS", 429)}) {
            doThrow(new DomainException(c.codigo(), "x")).when(padron).consultar(any(), any());
            mvc.perform(get("/v1/admin/consultas/ruc/20100066603").requestAttr(AdministradorActual.ATRIBUTO, UUID.randomUUID()))
                    .andExpect(status().is(c.http())).andExpect(jsonPath("$.codigo").value(c.codigo()));
        }
    }
}
