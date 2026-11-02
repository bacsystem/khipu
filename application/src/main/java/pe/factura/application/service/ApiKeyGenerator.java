package pe.factura.application.service;

import pe.factura.domain.tenant.ApiKey;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public final class ApiKeyGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ApiKeyGenerator() {}
    public static String generar() {
        byte[] b = new byte[30]; RANDOM.nextBytes(b);
        return "fk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public static String hash(String key, String pepper) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((key + pepper).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    /**
     * De qué pepper es un hash (S2), sin guardar el pepper: los primeros 16 hex de un SHA-256 con dominio propio, para que no coincida con el hash de
     * ninguna key.
     */
    public static String huellaDePepper(String pepper) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("khipu:huella-de-pepper:" + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String prefijo(String key) { return key.substring(0, Math.min(10, key.length())); }

    /**
     * La API key a guardar para {@code keyEnClaro}: solo el hash (con el pepper) y el prefijo, activa y sin revocar. El único lugar
     * que decide cómo se guarda una key nueva, para que el alta de una empresa y el alta asistida (#188) no puedan divergir.
     */
    public static ApiKey nueva(UUID tenantId, String keyEnClaro, String pepper, Instant creadaEn) {
        return new ApiKey(UUID.randomUUID(), tenantId, hash(keyEnClaro, pepper), prefijo(keyEnClaro), true, creadaEn, null);
    }
}
