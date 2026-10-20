package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import pe.factura.application.port.out.SunatApiValidezGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Consulta integrada de comprobante de pago» por la API REST de SUNAT (#318), según el «Manual de Consulta Integrada de Comprobante de Pago por Servicio Web»
 * v2.0 (cpe.sunat.gob.pe/sites/default/files/inline-files/Manual-de-Consulta-Integrada-de-Comprobante-de-Pago-por-ServicioWEB_v2_0.pdf):
 * <ul>
 *   <li>Token: {@code POST <seguridad>/v1/clientesextranet/<client_id>/oauth2/token/}, grant {@code client_credentials}, scope
 *       {@value #SCOPE} (sección B).</li>
 *   <li>Consulta: {@code POST <api>/v1/contribuyente/contribuyentes/<RUC de quien consulta>/validarcomprobante} con {@code numRuc}, {@code codComp},
 *       {@code numeroSerie}, {@code numero}, {@code fechaEmision} (dd/mm/yyyy, obligatoria) y {@code monto} (solo comprobantes electrónicos) (sección C).</li>
 * </ul>
 * {@code numero} y {@code monto} van como texto, como en el ejemplo del manual ({@code "numero": "749"}, {@code "monto": "236.00"}); la tabla los da como
 * entero y decimal, y el ejemplo es lo único que SUNAT muestra funcionando. Asumido sin credenciales reales: ver el pendiente de prueba controlada del #318.
 *
 * <p>SUNAT no publica una instancia de pruebas de esta API: la consulta lee datos reales y no escribe nada, así que por defecto la URL de beta es la misma
 * que la de producción (configurable).
 */
public class RestValidezGateway implements SunatApiValidezGateway {
    static final String SCOPE = "https://api.sunat.gob.pe/v1/contribuyente/contribuyentes";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UrlPorEntorno seguridad;
    private final UrlPorEntorno api;
    private final ClienteApiSunat cliente;

    public RestValidezGateway(UrlPorEntorno seguridad, UrlPorEntorno api, ClienteApiSunat cliente) {
        this.seguridad = seguridad; this.api = api; this.cliente = cliente;
    }

    @Override
    public Validez validar(Tenant t, String rucEmisor, String tipo, String serie, long numero, LocalDate fechaEmision, BigDecimal importeTotal) {
        CredencialesApiSunat c = t.apiSunat();
        if (c == null) throw new DomainException("CREDENCIALES_API_SUNAT_NO_CARGADAS", "La empresa no tiene credenciales API SUNAT");
        if (fechaEmision == null) throw new DomainException("PARAMETRO_INVALIDO", "fecha: obligatoria para la consulta por la API de SUNAT");
        PedidoDeToken token = PedidoDeToken.clientCredentials(
                seguridad.para(t.entorno()) + "/v1/clientesextranet/" + c.clientId() + "/oauth2/token/", SCOPE, c.clientId(), c.clientSecret());
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("numRuc", rucEmisor);
        cuerpo.put("codComp", tipo);
        cuerpo.put("numeroSerie", serie);
        cuerpo.put("numero", String.valueOf(numero));
        cuerpo.put("fechaEmision", fechaEmision.format(FECHA));
        if (importeTotal != null) cuerpo.put("monto", importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());

        JsonNode r = cliente.postJson(token, api.para(t.entorno()) + "/v1/contribuyente/contribuyentes/" + t.ruc() + "/validarcomprobante", cuerpo);

        if (!r.path("success").asBoolean(false)) {
            String cod = ClienteApiSunat.texto(r, "errorCode", "cod");
            String msg = token.sinSecretos(ClienteApiSunat.texto(r, "message", "msg"));
            throw new SunatRechazoException(cod.isBlank() ? "0000" : cod, msg.isBlank() ? "SUNAT respondió success: false sin mensaje" : msg);
        }
        JsonNode data = r.path("data");
        String estadoCp = data.path("estadoCp").asText("");
        if (estadoCp.isBlank()) throw new SunatTransientException("0000", "La API de SUNAT respondió sin data.estadoCp");
        List<String> obs = new ArrayList<>();
        // El manual escribe «data.Observaciones» en la tabla y «observaciones» en el ejemplo: se aceptan las dos.
        JsonNode o = data.has("observaciones") ? data.path("observaciones") : data.path("Observaciones");
        for (JsonNode x : o) if (!x.asText("").isBlank()) obs.add(x.asText().strip());
        return new Validez(estadoCp.strip(), vacioANulo(data.path("estadoRuc").asText("")), vacioANulo(data.path("condDomiRuc").asText("")), obs);
    }

    private static String vacioANulo(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
