package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CatalogoController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class CatalogoControllerTest {
    @Autowired MockMvc mvc;

    @Test void indiceYCatalogoConColumnasAdicionales() throws Exception {
        mvc.perform(get("/v1/catalogos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id=='07')].nombre").value("Código de tipo de afectación del IGV"));
        mvc.perform(get("/v1/catalogos/07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.columnas[2]").value("Codigo de tributo"))
                .andExpect(jsonPath("$.datos.entradas[0].codigo").value("10"))
                .andExpect(jsonPath("$.datos.entradas[0].extra['Codigo de tributo']").value("1000"));
    }

    @Test void catalogoInexistenteEs404() throws Exception {
        mvc.perform(get("/v1/catalogos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
