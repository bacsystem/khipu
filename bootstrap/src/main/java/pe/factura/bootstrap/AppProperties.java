package pe.factura.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String mode, String masterKey, String apiKeyPepper, String platformAdminKey,
                            String jwtSecret, String portalUrl,
                            Storage storage, Sunat sunat, Outbox outbox, Mail mail, Integridad integridad, Monitor monitor, String zonaHoraria,
                            String relojDesplazamiento, Alertas alertas) {
    // relojDesplazamiento (RELOJ_DESPLAZAMIENTO, #363): duración ISO-8601 que se suma al reloj del backend, solo para probar plazos en un ambiente de pruebas
    // (-P8D = ocho días antes). Vacío = hora real. Con valor, no arranca si hay empresas en PRODUCCION. Ver RelojDeLaAplicacion y deploy/README.md §10.

    /** El desplazamiento del reloj ya leído (cero si no hay): un valor inválido o fuera del tope impide arrancar. */
    public java.time.Duration desplazamientoDelReloj() { return RelojDeLaAplicacion.desplazamiento(relojDesplazamiento); }

    /** {@code type}: {@code fs} (disco local, desarrollo) o {@code s3} (S3/MinIO/B2/R2, producción: #38). */
    public record Storage(String type, String fsRoot, S3 s3) {
        public record S3(String bucket, String region, String endpoint, String accessKey, String secretKey, boolean pathStyle) {}
    }
    /** Verificación diaria de integridad del storage: ventana de {@code dias} hacia atrás. */
    public record Integridad(int dias) {}
    /** {@code consultaBetaUrl}/{@code validezBetaUrl} vacías: SUNAT no publica esos servicios en e-beta; se dejan configurables por si aparecen. */
    public record Sunat(int timeoutSeconds, String betaUrl, String prodUrl, String consultaUrl, String consultaBetaUrl, String validezUrl, String validezBetaUrl) {}
    /**
     * Monitor global del backoffice. {@code resumenSinRespuestaHoras} (#362 B14): horas que un resumen diario o una baja con ticket puede estar sin respuesta de SUNAT antes
     * de que el monitor alerte; rango 1–168 validado en {@link AppConfig#umbralDeResumenesSinRespuesta}. No cambia el comportamiento del worker, que reconsulta sin tope.
     */
    public record Monitor(int resumenSinRespuestaHoras) {
        public static final int HORAS_POR_DEFECTO = 6;
    }
    /**
     * Alertas al operador (#341). {@code email}: direcciones separadas por coma; {@code webhookUrl}: un webhook de Slack, Discord o similar; sin ninguno solo se escribe en el log.
     * {@code repetirHoras}: cada cuánto se repite un problema que sigue. {@code errorEnvioMinutos} y {@code errorEnvioCantidad}: cuántos comprobantes llevan ese tiempo en
     * ERROR_ENVIO para alertar. {@code respaldoHoras}: lo más que puede pasar sin un respaldo bueno (0 apaga la alerta). {@code fueraDePlazoHoras}: ventana en que se cuentan los
     * comprobantes que pasaron a FUERA_DE_PLAZO. Rangos validados en {@link AppConfig#umbralesDeAlertas}.
     */
    public record Alertas(String email, String webhookUrl, int repetirHoras, int errorEnvioMinutos, int errorEnvioCantidad, int respaldoHoras, int fueraDePlazoHoras) {
        public static final int REPETIR_HORAS_POR_DEFECTO = 24;
        public static final int ERROR_ENVIO_MINUTOS_POR_DEFECTO = 30;
        public static final int ERROR_ENVIO_CANTIDAD_POR_DEFECTO = 5;
        public static final int FUERA_DE_PLAZO_HORAS_POR_DEFECTO = 24;
    }
    public record Outbox(long intervaloMs, int maxIntentos) {}
    /** Sin SMTP habilitado, los correos (recuperación de contraseña) se escriben en el log. */
    public record Mail(boolean habilitado, String remitente) {}
}
