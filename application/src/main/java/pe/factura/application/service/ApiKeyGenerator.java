package pe.factura.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

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
    public static String prefijo(String key) { return key.substring(0, Math.min(10, key.length())); }
}
