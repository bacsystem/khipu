package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminOrigenController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AdminOrigenControllerTest {
    @Autowired MockMvc mvc;

    /** Es la misma IP que `AdministradorActual.actor` escribiría en la bitácora: sirve para calibrar la cadena de proxies (#208). */
    @Test void devuelveLaIpQueElBackendResolvioParaLaPeticion() throws Exception {
        mvc.perform(get("/v1/admin/origen").with(req -> { req.setRemoteAddr("203.0.113.7"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("exito"))
                .andExpect(jsonPath("$.datos.ip").value("203.0.113.7"));
    }

    /** Si mostrara otra grafía, quien calibra compararía algo distinto de lo que la bitácora guardará. */
    @Test void muestraUnaIpv6EnLaMismaFormaQueLaBitacora() throws Exception {
        mvc.perform(get("/v1/admin/origen").with(req -> { req.setRemoteAddr("2001:db8::1"); return req; }))
                .andExpect(jsonPath("$.datos.ip").value("2001:db8:0:0:0:0:0:1"));
    }

    @Test void noDevuelveNadaMasQueLaIp() throws Exception {
        // La cabecera cruda no se devuelve: es lo que el cliente puso, y reflejarla no aporta nada a quien calibra.
        mvc.perform(get("/v1/admin/origen").header("X-Forwarded-For", "6.6.6.6").with(req -> { req.setRemoteAddr("203.0.113.7"); return req; }))
                .andExpect(jsonPath("$.datos.ip").value("203.0.113.7"))
                .andExpect(jsonPath("$.datos.x_forwarded_for").doesNotExist())
                .andExpect(jsonPath("$.datos.forwarded_for").doesNotExist());
    }
}
