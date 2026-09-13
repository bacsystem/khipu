package pe.factura.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.factura.adapters.crypto.AesGcmSecretCipher;
import pe.factura.adapters.persistence.*;
import pe.factura.adapters.rest.ApiKeyFilter;
import pe.factura.adapters.rest.PlatformKeyFilter;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.signing.XmlDsigSigner;
import pe.factura.adapters.storage.FileSystemDocumentStorage;
import pe.factura.adapters.sunat.SoapBillingGateway;
import pe.factura.adapters.sunat.SunatUrls;
import pe.factura.adapters.sunat.XmlCdrParser;
import pe.factura.adapters.ubl.FreemarkerUblGenerator;
import pe.factura.adapters.ubl.JaxpXsdValidator;
import pe.factura.application.port.in.*;
import pe.factura.application.port.out.*;
import pe.factura.application.service.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    private static final String PLACEHOLDER = "cambiar-en-produccion";

    /**
     * Ruling de seguridad: MASTER_KEY y API_KEY_PEPPER no tienen valor por defecto.
     * Si alguno falta al arrancar la aplicación, o conserva el placeholder histórico
     * "cambiar-en-produccion", se aborta con un mensaje explícito en lugar de dejar que
     * un cifrado o hash silenciosamente inseguro llegue a producción.
     */
    static void exigirSecretosDePlataforma(AppProperties p) {
        if (esInvalido(p.masterKey()) || esInvalido(p.apiKeyPepper())) {
            throw new IllegalStateException("MASTER_KEY y API_KEY_PEPPER son obligatorios y no pueden ser '" + PLACEHOLDER + "'");
        }
    }

    private static boolean esInvalido(String secreto) {
        return secreto == null || secreto.isBlank() || secreto.trim().equalsIgnoreCase(PLACEHOLDER);
    }

    @Bean Clock clock(AppProperties p) { return Clock.system(ZoneId.of(p.zonaHoraria())); }
    @Bean SecretCipher secretCipher(AppProperties p) { exigirSecretosDePlataforma(p); return new AesGcmSecretCipher(p.masterKey()); }
    @Bean UnitOfWork unitOfWork(PlatformTransactionManager tm) { return new JdbcUnitOfWork(tm); }

    @Bean TenantRepository tenantRepository(JdbcTemplate jdbc, SecretCipher c) { return new JdbcTenantRepository(jdbc, c); }
    @Bean ApiKeyRepository apiKeyRepository(JdbcTemplate jdbc) { return new JdbcApiKeyRepository(jdbc); }
    @Bean SerieRepository serieRepository(JdbcTemplate jdbc) { return new JdbcSerieRepository(jdbc); }
    @Bean ComprobanteRepository comprobanteRepository(JdbcTemplate jdbc) { return new JdbcComprobanteRepository(jdbc); }
    @Bean OutboxRepository outboxRepository(JdbcTemplate jdbc) { return new JdbcOutboxRepository(jdbc); }

    @Bean DocumentStorage documentStorage(AppProperties p) { return new FileSystemDocumentStorage(Path.of(p.storage().fsRoot())); }
    @Bean UblGenerator ublGenerator() { return new FreemarkerUblGenerator(); }
    @Bean XsdValidator xsdValidator() { return new JaxpXsdValidator(); }
    @Bean XmlSigner xmlSigner() { return new XmlDsigSigner(); }
    @Bean CdrParser cdrParser() { return new XmlCdrParser(); }
    @Bean SunatBillingGateway sunatBillingGateway(AppProperties p) {
        return new SoapBillingGateway(new SunatUrls(p.sunat().betaUrl(), p.sunat().prodUrl()), Duration.ofSeconds(p.sunat().timeoutSeconds()));
    }

    @Bean EnviarDocumentoUseCase enviarDocumento(ComprobanteRepository c, TenantRepository t, DocumentStorage s, SunatBillingGateway g, CdrParser p,
                                                OutboxRepository o, UnitOfWork u, Clock clock) {
        return new EnviarDocumentoService(c, t, s, g, p, o, u, clock);
    }
    @Bean EmitirComprobanteUseCase emitirComprobante(ComprobanteRepository c, SerieRepository se, TenantRepository t, DocumentStorage s,
                                                    UblGenerator ubl, XsdValidator xsd, XmlSigner signer, EnviarDocumentoUseCase enviar, UnitOfWork u, Clock clock) {
        return new EmitirComprobanteService(c, se, t, s, ubl, xsd, signer, enviar, u, clock);
    }
    @Bean ConsultarComprobanteUseCase consultarComprobante(ComprobanteRepository c, DocumentStorage s) { return new ConsultarComprobanteService(c, s); }
    @Bean AdministrarTenantUseCase administrarTenant(TenantRepository t, SerieRepository s, ApiKeyRepository k, UnitOfWork u, AppProperties p, Clock clock) {
        return new AdministrarTenantService(t, s, k, u, p.apiKeyPepper(), clock);
    }

    @Bean OutboxWorker outboxWorker(OutboxRepository o, UnitOfWork u, EnviarDocumentoUseCase e, Clock clock, AppProperties p) {
        return new OutboxWorker(o, u, e, clock, p.outbox().maxIntentos());
    }

    // Ambos filtros se registran sobre "/v1/*": el contenedor los aplica sobre la ruta ya decodificada y
    // normalizada, lo que actúa como segunda barrera además de RutaRequest dentro de cada filtro.
    @Bean FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ApiKeyRepository k, AppProperties p) {
        exigirSecretosDePlataforma(p);
        var f = new FilterRegistrationBean<>(new ApiKeyFilter(k, p.apiKeyPepper()));
        f.addUrlPatterns("/v1/*"); f.setOrder(10); return f;
    }
    @Bean FilterRegistrationBean<PlatformKeyFilter> platformKeyFilter(AppProperties p) {
        var f = new FilterRegistrationBean<>(new PlatformKeyFilter(p.platformAdminKey()));
        f.addUrlPatterns("/v1/*"); f.setOrder(5); return f;
    }
}
