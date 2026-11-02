package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase.PreferenciasDeEmision;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las preferencias de emisión de la empresa en snake_case y envueltas en {@code datos}. Que solo un ADMIN las cambie lo prueba {@code JwtFilterTest} (el rol lo corta el filtro). */
@WebMvcTest(controllers = PreferenciasDeEmisionController.class)
@Import(GlobalExceptionHandler.class)
class PreferenciasDeEmisionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PreferenciasDeEmisionUseCase preferencias;
    UUID tenant = UUID.randomUUID();

    @Test void seVenLasDeLaEmpresaDelRequest() throws Exception {
        when(preferencias.obtener(tenant)).thenReturn(new PreferenciasDeEmision(false));
        mvc.perform(get("/v1/empresa/preferencias-emision").requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.opciones_avanzadas").value(false));
    }

    @Test void seGuardanYDevuelveLoGuardado() throws Exception {
        when(preferencias.guardar(tenant, new PreferenciasDeEmision(true))).thenReturn(new PreferenciasDeEmision(true));
        mvc.perform(put("/v1/empresa/preferencias-emision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"opciones_avanzadas\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.opciones_avanzadas").value(true));
        verify(preferencias).guardar(tenant, new PreferenciasDeEmision(true));
    }

    @Test void sinElValorEsUnaValidacionYNoSeGuardaNada() throws Exception {
        mvc.perform(put("/v1/empresa/preferencias-emision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verify(preferencias, never()).guardar(any(), any());
    }

    @Test void unaEmpresaQueNoExisteEs404() throws Exception {
        when(preferencias.guardar(any(), any())).thenThrow(new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        mvc.perform(put("/v1/empresa/preferencias-emision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content("{\"opciones_avanzadas\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
