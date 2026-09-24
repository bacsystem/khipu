package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #174. Clase separada de {@link AuthControllerTest} porque el valor de {@code app.registro-abierto} se fija
 * a nivel de contexto de Spring: no se puede alternar por método dentro de la misma clase de test.
 *
 * <p>A propósito **no** declara la propiedad: así se prueba el default real de fábrica (cerrado), no un valor
 * puesto a mano en el test. Es la propiedad de seguridad que importa — un despliegue que se olvida de fijar
 * {@code REGISTRO_ABIERTO} queda cerrado, no abierto.
 */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerRegistroCerradoTest {
    @Autowired MockMvc mvc;
    @MockBean AutenticarUsuarioUseCase auth;

    @Test void conElRegistroCerradoRechazaAntesDeLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/v1/auth/registro").contentType("application/json")
                        .content("{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value("error"))
                .andExpect(jsonPath("$.codigo").value("REGISTRO_CERRADO"))
                .andExpect(jsonPath("$.mensaje").value("El registro está por invitación: escríbenos para pedir acceso."));
        // No solo el HTTP: la cuenta tampoco se intenta crear. Sin esto, un mock mal configurado podría dar
        // el mismo 403 por una razón distinta y el test seguiría en verde.
        verifyNoInteractions(auth);
    }
}
