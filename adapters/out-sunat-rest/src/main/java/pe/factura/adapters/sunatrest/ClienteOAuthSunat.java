package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatTransientException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens OAuth2 de {@code api-seguridad.sunat.gob.pe} (#318), con caché hasta que venzan. SUNAT da hoy una hora ({@code expires_in: 3600}); se renueva
 * {@link #MARGEN} antes para que un token no venza a mitad de una llamada. Una sola petición de token por credencial a la vez: si dos consultas de la misma
 * empresa llegan con el token vencido, la segunda espera y usa el de la primera.
 *
 * <p>Errores: 400/401/403 del servicio de token son las credenciales (client_id o client_secret equivocados, aplicación eliminada en SOL, clave SOL incorrecta
 * en el grant password): {@link SunatCredencialesException}, que no cambia por reintentar. Lo demás (5xx, sin respuesta, respuesta sin token) es
 * {@link SunatTransientException}. Ningún mensaje lleva el secreto, la clave SOL ni el token.
 */
public class ClienteOAuthSunat {
    static final Duration MARGEN = Duration.ofSeconds(60);
    /** Si SUNAT no dice cuánto dura (no debería pasar), cinco minutos: lo bastante para no pedir uno en cada llamada, lo bastante corto si en verdad duraba menos. */
    static final Duration SIN_EXPIRES_IN = Duration.ofMinutes(5);

    private record TokenVigente(String valor, Instant vence) {}

    private final TransporteHttp http;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentHashMap<String, TokenVigente> cache = new ConcurrentHashMap<>();

    ClienteOAuthSunat(TransporteHttp http, Clock clock) { this.http = http; this.clock = clock; }

    public ClienteOAuthSunat(Duration conexion, Duration espera, Clock clock) { this(new TransporteHttp(conexion, espera), clock); }

    /** El token vigente para esas credenciales; pide uno nuevo si no hay o está por vencer. */
    public String token(PedidoDeToken p) {
        // compute serializa por clave: dos llamadas con el token vencido no piden dos tokens.
        return cache.compute(p.claveDeCache(), (k, actual) -> actual != null && clock.instant().isBefore(actual.vence()) ? actual : pedir(p)).valor();
    }

    /** Descarta el token en caché: SUNAT lo rechazó (401) antes de lo que decía {@code expires_in}, o se cambió la credencial. */
    public void invalidar(PedidoDeToken p) { cache.remove(p.claveDeCache()); }

    private TokenVigente pedir(PedidoDeToken p) {
        TransporteHttp.Respuesta r = http.post(p.url(), "application/x-www-form-urlencoded", p.formulario(), null);
        if (r.status() == 400 || r.status() == 401 || r.status() == 403)
            throw new SunatCredencialesException(String.valueOf(r.status()), "SUNAT no acepta las credenciales API SUNAT (HTTP " + r.status() + detalle(r.cuerpo(), p) + ")");
        if (r.status() < 200 || r.status() >= 300)
            throw new SunatTransientException("0000", "El servicio de token de SUNAT respondió HTTP " + r.status() + detalle(r.cuerpo(), p));
        JsonNode raiz;
        try {
            raiz = json.readTree(r.cuerpo());
        } catch (IOException e) {
            throw new SunatTransientException("0000", "El servicio de token de SUNAT respondió algo que no es JSON");
        }
        String token = raiz == null ? null : raiz.path("access_token").asText(null);
        if (token == null || token.isBlank()) throw new SunatTransientException("0000", "El servicio de token de SUNAT respondió sin access_token");
        long segundos = raiz.path("expires_in").asLong(0);
        Duration vida = segundos > 0 ? Duration.ofSeconds(segundos) : SIN_EXPIRES_IN;
        Duration util = vida.compareTo(MARGEN.multipliedBy(2)) > 0 ? vida.minus(MARGEN) : vida.dividedBy(2);
        return new TokenVigente(token, clock.instant().plus(util));
    }

    /**
     * Lo que SUNAT explica del error, acotado y sin secretos: los campos de error de OAuth2 ({@code error}, {@code error_description}) o los de sus manuales
     * ({@code cod}, {@code msg}). Nunca el cuerpo entero: podría repetir lo enviado.
     */
    String detalle(String cuerpo, PedidoDeToken p) {
        try {
            JsonNode n = json.readTree(cuerpo);
            if (n == null || !n.isObject()) return "";
            StringBuilder sb = new StringBuilder();
            for (String campo : new String[]{"error", "error_description", "cod", "msg"}) {
                String v = n.path(campo).asText("");
                if (!v.isBlank()) sb.append(sb.isEmpty() ? ": " : " - ").append(v.strip());
            }
            String d = p.sinSecretos(sb.toString());
            return d.length() > 200 ? d.substring(0, 200) + "…" : d;
        } catch (IOException e) {
            return "";
        }
    }
}
