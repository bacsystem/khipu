package pe.factura.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String mode, String masterKey, String apiKeyPepper, String platformAdminKey,
                            Storage storage, Sunat sunat, Outbox outbox, String zonaHoraria) {
    public record Storage(String type, String fsRoot) {}
    public record Sunat(int timeoutSeconds, String betaUrl, String prodUrl) {}
    public record Outbox(long intervaloMs, int maxIntentos) {}
}
