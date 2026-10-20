package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Llamada JSON autenticada a una API REST de SUNAT (#318): pide el token a {@link ClienteOAuthSunat}, lo manda como {@code Authorization: Bearer} y traduce
 * la respuesta según el «Manejo de errores» común de sus manuales (cuerpo {@code cod}/{@code msg}/{@code errors}). Es la pieza que reusan la validez, la
 * GRE (#39) y el SIRE (#322): no sabe nada de ningún servicio en particular.
 *
 * <ul>
 *   <li>2xx: el JSON de la respuesta.</li>
 *   <li>401: el token pudo vencer o revocarse antes de tiempo; se descarta, se pide otro y se reintenta UNA vez. Si vuelve a 401, {@link SunatCredencialesException}.</li>
 *   <li>403: la aplicación registrada en SOL no tiene permiso para ese servicio: {@link SunatCredencialesException} (se corrige en SOL, no reintentando).</li>
 *   <li>400 y 422: SUNAT entendió el pedido y lo rechaza por sus datos: {@link SunatRechazoException}. Reintentar no lo cambia (que el servicio pregunte igual por SOAP mientras el contrato no esté verificado lo decide {@code ConsultarValidezService}).</li>
 *   <li>Lo demás (404, 405, 429, 5xx…) y sin respuesta: {@link SunatTransientException}. Un 404 de toda la ruta es una URL mal configurada o un servicio
 *       caído, no una respuesta sobre el comprobante.</li>
 * </ul>
 */
public class ClienteApiSunat {
    private final ClienteOAuthSunat oauth;
    private final TransporteHttp http;
    private final ObjectMapper json = new ObjectMapper();

    ClienteApiSunat(ClienteOAuthSunat oauth, TransporteHttp http) { this.oauth = oauth; this.http = http; }

    /** {@code conexion}: tiempo para conectar; {@code espera}: tiempo total de cada llamada (token o servicio). */
    public ClienteApiSunat(Duration conexion, Duration espera, Clock clock) { this(new TransporteHttp(conexion, espera), clock); }

    private ClienteApiSunat(TransporteHttp http, Clock clock) { this(new ClienteOAuthSunat(http, clock), http); }

    public JsonNode postJson(PedidoDeToken credenciales, String url, Object cuerpo) {
        String body;
        try {
            body = json.writeValueAsString(cuerpo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cuerpo no serializable para " + url, e);
        }
        TransporteHttp.Respuesta r = http.post(url, "application/json", body, "Bearer " + oauth.token(credenciales));
        if (r.status() == 401) {
            oauth.invalidar(credenciales);
            r = http.post(url, "application/json", body, "Bearer " + oauth.token(credenciales));
            if (r.status() == 401) throw new SunatCredencialesException("401", "SUNAT rechazó el token de la API SUNAT aun recién renovado (HTTP 401)" + motivo(r.cuerpo(), credenciales));
        }
        if (r.status() == 403)
            throw new SunatCredencialesException("403", "La aplicación registrada en «Credenciales de API SUNAT» no tiene permiso para este servicio (HTTP 403)" + motivo(r.cuerpo(), credenciales));
        if (r.status() == 400 || r.status() == 422) {
            JsonNode n = leer(r.cuerpo());
            String cod = n == null ? "" : texto(n, "errorCode", "cod");
            throw new SunatRechazoException(cod.isBlank() ? String.valueOf(r.status()) : cod, "HTTP " + r.status() + motivo(r.cuerpo(), credenciales));
        }
        if (r.status() < 200 || r.status() >= 300) throw new SunatTransientException("0000", "La API de SUNAT respondió HTTP " + r.status() + motivo(r.cuerpo(), credenciales));
        JsonNode n = leer(r.cuerpo());
        if (n == null) throw new SunatTransientException("0000", "La API de SUNAT respondió algo que no es JSON");
        return n;
    }

    /** Explicación de SUNAT, acotada y sin secretos: {@code message} o {@code msg}, y el detalle de {@code errors[]} ({@code codError - desError}). */
    String motivo(String cuerpo, PedidoDeToken p) {
        JsonNode n = leer(cuerpo);
        if (n == null || !n.isObject()) return "";
        StringBuilder sb = new StringBuilder(texto(n, "message", "msg"));
        for (JsonNode e : n.path("errors")) {
            String d = (e.path("codError").asText("") + " " + e.path("desError").asText("")).strip();
            if (!d.isBlank()) sb.append(sb.isEmpty() ? "" : "; ").append(d);
        }
        String m = p.sinSecretos(sb.toString().strip());
        if (m.isBlank()) return "";
        return ": " + (m.length() > 300 ? m.substring(0, 300) + "…" : m);
    }

    private JsonNode leer(String cuerpo) {
        try {
            return cuerpo == null || cuerpo.isBlank() ? null : json.readTree(cuerpo);
        } catch (IOException e) {
            return null;
        }
    }

    static String texto(JsonNode n, String... campos) {
        for (String c : campos) {
            String v = n.path(c).asText("");
            if (!v.isBlank()) return v.strip();
        }
        return "";
    }
}
