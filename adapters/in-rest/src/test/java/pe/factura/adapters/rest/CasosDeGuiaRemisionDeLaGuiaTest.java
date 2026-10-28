package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.adapters.rest.dto.GuiaRemisionRequest;
import pe.factura.application.port.in.ConsultarGuiaRemisionUseCase;
import pe.factura.application.port.in.EmitirGuiaRemisionCommand;
import pe.factura.application.port.in.EmitirGuiaRemisionUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.PuntoDeTraslado;
import pe.factura.domain.documento.Traslado;

import java.nio.file.Files;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.factura.adapters.rest.CasosDeLaGuia.request;

/**
 * #39: los casos de guía de remisión de la guía para desarrolladores ({@code portal/.../casos-emision.ts}) son documentación pública. Cada request pasa por Jackson
 * sin campos desconocidos, por Bean Validation y por las reglas reales del dominio ({@link GuiaRemision#crear}): un ejemplo que khipu rechazaría lo tira.
 */
@WebMvcTest(controllers = GuiaRemisionController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class CasosDeGuiaRemisionDeLaGuiaTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String RUC_EMISOR = "20100066603";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean EmitirGuiaRemisionUseCase emitir;
    @MockBean EnviarGuiaRemisionUseCase enviar;
    @MockBean ConsultarGuiaRemisionUseCase consultar;
    UUID tenant = UUID.randomUUID();

    @Test void losRequestsDeGuiaDeRemisionDeLaGuiaPasanLaValidacionReal() throws Exception {
        for (String[] caso : new String[][]{{"guia-remision-remitente", "09"}, {"guia-remision-transportista", "31"}}) {
            String request = request(caso[0]);
            json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(request, GuiaRemisionRequest.class);
            when(emitir.emitir(eq(tenant), any(), isNull())).thenAnswer(inv -> new EmitirGuiaRemisionUseCase.Emision(guiaDe(inv.getArgument(1)), false));
            mvc.perform(post("/v1/guias-remision").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(request))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.datos.tipo").value(caso[1]));
        }
    }

    /**
     * Lo que hace EmitirGuiaRemisionService con el comando: resuelve el establecimiento propio (aquí, el 0001 de la empresa de ejemplo) y aplica las reglas del
     * dominio, con el reloj en la fecha de emisión del ejemplo.
     */
    private static GuiaRemision guiaDe(EmitirGuiaRemisionCommand cmd) {
        Traslado t = cmd.traslado();
        PuntoDeTraslado partida = t.partida().ubigeo() == null ? new PuntoDeTraslado("150101", "Av. Argentina 1234, Lima", t.partida().codigoEstablecimiento(), RUC_EMISOR) : t.partida();
        Traslado resuelto = new Traslado(t.fechaInicio(), t.pesoBruto(), t.unidadPeso(), t.bultos(), partida, t.llegada(), t.vehiculos(), t.conductores(), t.indicadores());
        Clock reloj = Clock.fixed(java.time.LocalDate.of(2026, 10, 20).atTime(12, 0).atZone(LIMA).toInstant(), LIMA);
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), RUC_EMISOR, cmd.serie(), cmd.fechaEmision(), cmd.destinatario(), cmd.datos(), resuelto, cmd.items(), cmd.documentos(),
                cmd.observaciones(), reloj);
        g.asignarNumero(1, RUC_EMISOR);
        return g;
    }

    @Test void losCasosDeGuiaDicenSuEndpoint() throws Exception {
        String archivo = Files.readString(CasosDeLaGuia.ARCHIVO);
        for (String id : List.of("guia-remision-remitente", "guia-remision-transportista"))
            assertThat(archivo).containsPattern(Pattern.compile("id: \"" + id + "\",\\s*endpoint: \"/v1/guias-remision\""));
    }
}
