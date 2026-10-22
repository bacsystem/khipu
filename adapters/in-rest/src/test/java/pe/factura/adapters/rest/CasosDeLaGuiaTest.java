package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pe.factura.adapters.rest.dto.FacturaRequest;
import pe.factura.adapters.rest.dto.LiquidacionCompraRequest;
import pe.factura.application.port.in.*;
import pe.factura.domain.documento.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #351-H1/H6: los ejemplos de boleta de la guía para desarrolladores (`portal/.../casos-emision.ts`) son documentación pública. Este test los lee del
 * archivo del portal y los pasa por el backend real: cada request por Jackson (sin campos desconocidos), Bean Validation y el builder de la boleta; cada
 * respuesta de ejemplo debe ser un extracto de lo que serializa el controller (mismas rutas, mismos valores). Un ejemplo que el backend rechazaría, o un
 * campo que la API no devuelve donde el ejemplo lo pone, lo tira.
 */
@WebMvcTest(controllers = {FacturaController.class, BajaController.class, LiquidacionCompraController.class}, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
class CasosDeLaGuiaTest {
    private static final Path CASOS = Path.of("../../portal/src/components/developers/guia/casos-emision.ts");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String RUC_EMISOR = "20100066603";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean EmitirComprobanteUseCase emitir;
    @MockBean pe.factura.application.port.in.ValidarCompradorUseCase comprador;
    @MockBean EnviarDocumentoUseCase enviar;
    @MockBean ConsultarComprobanteUseCase consultar;
    @MockBean DarDeBajaUseCase bajas;
    @MockBean CompartirComprobanteUseCase compartir;
    @MockBean RecuperarCdrUseCase cdrs;
    @MockBean pe.factura.application.port.in.EnviarAlClienteUseCase envioAlCliente;

    UUID tenant = UUID.randomUUID();

    @Test void losRequestsDeBoletaDeLaGuiaPasanLaValidacionReal() throws Exception {
        for (String id : List.of("boleta-dni", "boleta-sin-documento")) {
            String request = request(id);
            // Más estricto que la API (que ignora campos desconocidos): un campo mal escrito en la guía se perdería en silencio.
            json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(request, FacturaRequest.class);
            when(emitir.emitirFactura(eq(tenant), any())).thenAnswer(inv -> boletaDe(inv.getArgument(1)));
            mvc.perform(post("/v1/facturas").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(request))
                    .andExpect(status().isCreated());
        }
    }

    /** #327: el caso de la liquidación de compra pasa por Jackson estricto, Bean Validation y el builder real, y su respuesta de ejemplo es un extracto de la real. */
    @Test void laLiquidacionDeCompraDeLaGuiaEsLaReal() throws Exception {
        String caso = request("liquidacion-compra");
        String pedido = caso.substring(0, caso.indexOf("// respuesta")).strip();
        json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(pedido, LiquidacionCompraRequest.class);
        when(emitir.emitirLiquidacionCompra(eq(tenant), any())).thenAnswer(inv -> liquidacionDe(inv.getArgument(1)));
        String real = mvc.perform(post("/v1/liquidaciones-compra").requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json").content(pedido))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        esExtracto(respuesta("liquidacion-compra"), json.readTree(real), "");
    }

    /** Lo que hace EmitirComprobanteService con el comando de la liquidación, con el reloj en la fecha de emisión. */
    private static Comprobante liquidacionDe(EmitirLiquidacionCommand cmd) {
        Comprobante c = Comprobante.liquidacionCompra(UUID.randomUUID(), cmd.serie(), cmd.fechaEmision(), cmd.moneda(), cmd.tipoOperacion(), cmd.vendedor(), cmd.liquidacion(), cmd.items())
                .referencias(cmd.referencias()).redondeo(cmd.redondeo()).tipoCambio(cmd.tipoCambio())
                .crear(Clock.fixed(cmd.fechaEmision().atTime(12, 0).atZone(LIMA).toInstant(), LIMA));
        c.asignarNumero(1, RUC_EMISOR);
        return c;
    }

    @Test void laRespuestaDelAltaTardiaEsUnExtractoDeLaReal() throws Exception {
        Comprobante b = boleta(88, LocalDate.of(2026, 9, 17));
        b.informarEnResumen("RC-20260923-1", false);
        b.aplicarCdr(new Cdr("0", "El Resumen diario RC-20260923-1, ha sido aceptado", List.of()), "r.zip");
        ComunicacionBaja alta = resumen(b, LocalDate.of(2026, 9, 23), ComunicacionBaja.Condicion.ALTA, "Pasó el envío individual: se informa en el resumen diario");
        when(enviar.enviar(tenant, b.id())).thenReturn(b);
        when(bajas.deComprobante(tenant, b.id())).thenReturn(List.of(alta));
        String real = mvc.perform(post("/v1/facturas/{id}/enviar", b.id()).requestAttr(TenantActual.ATRIBUTO, tenant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        esExtracto(respuesta("boleta-alta-tardia"), json.readTree(real), "");
    }

    @Test void laRespuestaDeLaBajaDeUnaBoletaEsUnExtractoDeLaReal() throws Exception {
        Comprobante b = boleta(87, LocalDate.of(2026, 9, 17));
        ComunicacionBaja baja = resumen(b, LocalDate.of(2026, 9, 18), ComunicacionBaja.Condicion.BAJA, "Venta anulada: error en el importe");
        when(bajas.solicitar(eq(tenant), eq(b.id()), any())).thenReturn(baja);
        String real = mvc.perform(post("/v1/facturas/{id}/baja", b.id()).requestAttr(TenantActual.ATRIBUTO, tenant).contentType("application/json")
                        .content(request("boleta-baja").substring(0, request("boleta-baja").indexOf('}') + 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        esExtracto(respuesta("boleta-baja"), json.readTree(real), "");
    }

    /** Lo que hace EmitirComprobanteService con el comando (sin la empresa): el builder de la boleta, con el reloj en la fecha de emisión. */
    private static Comprobante boletaDe(EmitirFacturaCommand cmd) {
        assertThat(cmd.serie()).startsWith("B");
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), cmd.serie(), cmd.fechaEmision(), cmd.moneda(), cmd.tipoOperacion(), cmd.receptor(), cmd.items())
                .fechaVencimiento(cmd.fechaVencimiento()).formaPago(cmd.formaPago()).descuentoGlobal(cmd.descuentoGlobal()).cargos(cmd.cargos())
                .detraccion(cmd.detraccion()).retencion(cmd.retencionIgv()).percepcion(cmd.percepcion()).anticipos(cmd.anticipos())
                .referencias(cmd.referencias()).redondeo(cmd.redondeo()).leyendas(cmd.leyendas()).exportacion(cmd.exportacion())
                .crear(Clock.fixed(cmd.fechaEmision().atTime(12, 0).atZone(LIMA).toInstant(), LIMA));
        c.anotar(cmd.observaciones());
        c.asignarNumero(1, RUC_EMISOR);
        return c;
    }

    private static Comprobante boleta(long numero, LocalDate fecha) {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", fecha, "PEN", "0101", new Receptor("1", "45678912", "MARÍA LÓPEZ QUISPE", null),
                List.of(new Item("P001", "Arroz extra 5 kg", "NIU", new BigDecimal("2"), new BigDecimal("23.60"), TipoAfectacionIgv.GRAVADO)))
                .crear(Clock.fixed(fecha.atTime(12, 0).atZone(LIMA).toInstant(), LIMA));
        c.asignarNumero(numero, RUC_EMISOR);
        c.firmar("HASH", "k.xml");
        return c;
    }

    private static ComunicacionBaja resumen(Comprobante c, LocalDate generacion, ComunicacionBaja.Condicion condicion, String motivo) {
        return ComunicacionBaja.rehidratar(UUID.randomUUID(), c.tenantId(), generacion, 1, c.id(), c.tipo(), true, c.serie(), c.numero(), c.fechaEmision(), motivo,
                condicion, ComunicacionBaja.EstadoBaja.ACEPTADA, "1789768174685", "k.xml", "r.zip", new Cdr("0", "aceptado", List.of()), 1, null, 1, 1);
    }

    /** Cada hoja del ejemplo existe en la respuesta real, en la misma ruta y con el mismo valor. */
    private static void esExtracto(JsonNode ejemplo, JsonNode real, String ruta) {
        if (ejemplo.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = ejemplo.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> campo = it.next();
                String hija = ruta + "/" + campo.getKey();
                assertThat(real.has(campo.getKey())).as("la API no devuelve %s", hija).isTrue();
                esExtracto(campo.getValue(), real.get(campo.getKey()), hija);
            }
        } else {
            assertThat(real.isValueNode()).as("%s es un valor en el ejemplo y no en la API", ruta).isTrue();
            assertThat(real.asText()).as(ruta).isEqualTo(ejemplo.asText());
        }
    }

    /** El template literal `request` del caso {@code id}, tal como lo muestra la guía. */
    private static String request(String id) throws IOException {
        Matcher m = Pattern.compile("id: \"" + Pattern.quote(id) + "\",.*?request: `(.*?)`,", Pattern.DOTALL).matcher(Files.readString(CASOS));
        assertThat(m.find()).as("caso %s en %s", id, CASOS.toAbsolutePath()).isTrue();
        assertThat(m.group(1)).as("el caso %s no interpola nada", id).doesNotContain("${");
        return m.group(1);
    }

    /** El JSON de la respuesta de ejemplo: el primer objeto después del comentario «// respuesta». */
    private JsonNode respuesta(String id) throws IOException {
        String r = request(id);
        int desde = r.indexOf('{', r.indexOf("// respuesta"));
        assertThat(r.indexOf("// respuesta")).as("el caso %s muestra una respuesta", id).isNotNegative();
        return json.readTree(r.substring(desde));
    }
}
