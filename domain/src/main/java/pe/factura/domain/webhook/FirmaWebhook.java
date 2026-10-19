package pe.factura.domain.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * La firma de una entrega de webhook (#19): HMAC-SHA256 con el secreto del endpoint sobre {@code "<t>.<cuerpo>"}, donde {@code t} es el instante del envío en segundos Unix.
 * Viaja en {@code X-Khipu-Signature: t=<t>,v1=<hex>}; quien recibe recalcula la firma con su copia del secreto, compara en tiempo constante y descarta un {@code t} viejo
 * (protege contra la repetición de una entrega capturada).
 */
public final class FirmaWebhook {
    private FirmaWebhook() {}

    public static final String CABECERA = "X-Khipu-Signature";

    public static String hmacHex(String secreto, long t, String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible", e);
        }
    }

    public static String cabecera(String secreto, long t, String cuerpo) { return "t=" + t + ",v1=" + hmacHex(secreto, t, cuerpo); }
}
