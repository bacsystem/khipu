package pe.factura.adapters.sunatrest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lo que se le pide a {@code api-seguridad.sunat.gob.pe} para obtener un token OAuth2. Las APIs de SUNAT usan dos grants:
 * <ul>
 *   <li>{@code client_credentials} — consulta integrada de validez: {@code POST /v1/clientesextranet/<client_id>/oauth2/token/} con {@code grant_type},
 *       {@code scope=https://api.sunat.gob.pe/v1/contribuyente/contribuyentes}, {@code client_id} y {@code client_secret} («Manual de Consulta Integrada de
 *       Comprobante de Pago por Servicio Web» v2.0, sección B, cpe.sunat.gob.pe).</li>
 *   <li>{@code password} — GRE (#39) y SIRE (#322): {@code POST /v1/clientessol/<client_id>/oauth2/token/} con, además, {@code username} = RUC + usuario SOL y
 *       {@code password} = clave SOL («Manual de servicios web plataforma nueva GRE», §1.2.b, docs/sunat/ref/manual_servicios_gre_rest.pdf).</li>
 * </ul>
 * Cuerpo {@code x-www-form-urlencoded} en los dos. El {@code toString} no muestra ni el secreto ni la clave SOL.
 */
public record PedidoDeToken(String url, String grantType, String scope, String clientId, String clientSecret, String usuario, String clave) {
    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String PASSWORD = "password";

    public static PedidoDeToken clientCredentials(String url, String scope, String clientId, String clientSecret) {
        return new PedidoDeToken(url, CLIENT_CREDENTIALS, scope, clientId, clientSecret, null, null);
    }

    /** {@code usuario}: RUC + usuario SOL, como lo pide SUNAT. */
    public static PedidoDeToken password(String url, String scope, String clientId, String clientSecret, String usuario, String clave) {
        return new PedidoDeToken(url, PASSWORD, scope, clientId, clientSecret, usuario, clave);
    }

    String formulario() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", grantType);
        f.put("scope", scope);
        f.put("client_id", clientId);
        f.put("client_secret", clientSecret);
        if (usuario != null) f.put("username", usuario);
        if (clave != null) f.put("password", clave);
        return f.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
    }

    /**
     * Identifica el token en la caché. Lleva una huella del secreto y de la clave SOL (no los valores): si la empresa los cambia, el token viejo no se reusa
     * con las credenciales nuevas, y la caché nunca guarda un secreto legible.
     */
    String claveDeCache() {
        return url + "|" + grantType + "|" + scope + "|" + clientId + "|" + (usuario == null ? "" : usuario) + "|" + huella(clientSecret + "\u0000" + (clave == null ? "" : clave));
    }

    /** Quita del texto el secreto y la clave SOL por si SUNAT los devolviera en un mensaje de error: lo que sale de aquí va a excepciones y logs. */
    String sinSecretos(String texto) {
        if (texto == null) return null;
        String r = texto;
        if (clientSecret != null && !clientSecret.isBlank()) r = r.replace(clientSecret, "***");
        if (clave != null && !clave.isBlank()) r = r.replace(clave, "***");
        return r;
    }

    @Override public String toString() {
        return "PedidoDeToken[url=" + url + ", grantType=" + grantType + ", scope=" + scope + ", clientId=" + clientId + ", clientSecret=***"
                + (usuario == null ? "" : ", usuario=" + usuario + ", clave=***") + "]";
    }

    private static String enc(String v) { return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8); }

    private static String huella(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
