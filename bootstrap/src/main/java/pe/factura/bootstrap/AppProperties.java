package pe.factura.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String mode, String masterKey, String apiKeyPepper, String platformAdminKey,
                            String jwtSecret, String portalUrl,
                            Storage storage, Sunat sunat, Outbox outbox, Mail mail, String zonaHoraria) {
    public record Storage(String type, String fsRoot) {}
    public record Sunat(int timeoutSeconds, String betaUrl, String prodUrl) {}
    public record Outbox(long intervaloMs, int maxIntentos) {}
    /** Sin SMTP habilitado, los correos (recuperación de contraseña) se escriben en el log. */
    public record Mail(boolean habilitado, String remitente) {}
}
