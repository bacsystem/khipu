package pe.factura.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String mode, String masterKey, String apiKeyPepper, String platformAdminKey,
                            String jwtSecret, String portalUrl,
                            Storage storage, Sunat sunat, Outbox outbox, Mail mail, String zonaHoraria) {
    public record Storage(String type, String fsRoot) {}
    /** {@code consultaBetaUrl}/{@code validezBetaUrl} vacías: SUNAT no publica esos servicios en e-beta; se dejan configurables por si aparecen. */
    public record Sunat(int timeoutSeconds, String betaUrl, String prodUrl, String consultaUrl, String consultaBetaUrl, String validezUrl, String validezBetaUrl) {}
    public record Outbox(long intervaloMs, int maxIntentos) {}
    /** Sin SMTP habilitado, los correos (recuperación de contraseña) se escriben en el log. */
    public record Mail(boolean habilitado, String remitente) {}
}
