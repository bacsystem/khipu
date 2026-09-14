package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.GestionarEmpresasUseCase;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmpresasController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class EmpresasControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GestionarEmpresasUseCase empresas;

    UUID cuenta = UUID.randomUUID();
    Tenant tenant = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, null, null);

    @Test void listarLasEmpresasDeLaCuenta() throws Exception {
        when(empresas.listar(cuenta)).thenReturn(List.of(tenant));
        mvc.perform(get("/v1/empresas").requestAttr(CuentaActual.ATRIBUTO, cuenta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].ruc").value("20100066603"))
                .andExpect(jsonPath("$.datos[0].tiene_certificado").value(false))
                .andExpect(jsonPath("$.datos[0].tiene_credenciales_sol").value(false));
    }

    @Test void crearEmpresaDevuelve201() throws Exception {
        when(empresas.crear(eq(cuenta), eq("20100066603"), eq("EMPRESA SAC"), eq(Entorno.BETA))).thenReturn(tenant);
        mvc.perform(post("/v1/empresas").requestAttr(CuentaActual.ATRIBUTO, cuenta).contentType("application/json")
                        .content("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA SAC\",\"entorno\":\"BETA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.ruc").value("20100066603"));
    }

    @Test void crearEmpresaConRucInvalidoEs422() throws Exception {
        mvc.perform(post("/v1/empresas").requestAttr(CuentaActual.ATRIBUTO, cuenta).contentType("application/json")
                        .content("{\"ruc\":\"123\",\"razon_social\":\"X\",\"entorno\":\"BETA\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
