package pe.factura.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import pe.factura.adapters.crypto.AesGcmSecretCipher;
import pe.factura.adapters.crypto.BcryptPasswordHasher;
import pe.factura.adapters.crypto.JwtTokenEmisor;
import pe.factura.adapters.mail.LogCorreoSender;
import pe.factura.adapters.mail.SmtpCorreoSender;
import pe.factura.adapters.persistence.*;
import pe.factura.adapters.rest.ApiKeyFilter;
import pe.factura.adapters.rest.JwtFilter;
import pe.factura.adapters.rest.PlatformKeyFilter;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.scheduler.PlazoEnvioWorker;
import pe.factura.adapters.signing.XmlDsigSigner;
import pe.factura.adapters.storage.FileSystemDocumentStorage;
import pe.factura.adapters.sunat.SoapBillingGateway;
import pe.factura.adapters.sunat.SunatUrls;
import pe.factura.adapters.sunat.SoapConsultaGateway;
import pe.factura.adapters.scheduler.RecuperarCdrWorker;
import pe.factura.adapters.sunat.XmlCdrParser;
import pe.factura.adapters.pdf.FlyingSaucerPdfGenerator;
import pe.factura.adapters.ubl.FreemarkerUblGenerator;
import pe.factura.adapters.ubl.JaxpXsdValidator;
import pe.factura.application.port.in.*;
import pe.factura.application.port.out.*;
import pe.factura.application.service.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

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

    /**
     * springdoc/swagger-core resuelven los schemas con su propio ObjectMapper por defecto
     * (io.swagger.v3.core.util.Json.mapper()), ajeno a spring.jackson.property-naming-strategy:
     * sin este bean, /openapi.json documenta los campos en camelCase aunque el runtime
     * serialice en snake_case. Al registrar un ModelResolver con el ObjectMapper de Spring,
     * springdoc lo detecta por tipo y reemplaza al que trae por defecto (ModelConverterRegistrar).
     */
    @Bean ModelResolver modelResolver(ObjectMapper objectMapper) { return new ModelResolver(objectMapper); }

    /**
     * Introducción de la referencia (/developers y /openapi.json): lo que un integrador necesita saber antes de leer
     * cada endpoint — autenticación, sobre de respuesta, estados del comprobante, errores y plazos. Se mantiene aquí y
     * no en el portal para que cualquier cliente OpenAPI (Postman, generadores) la reciba también.
     */
    @Bean OpenAPI openApiInfo(AppProperties p) {
        return new OpenAPI().info(new Info()
                .title("khipu API")
                .version("v1")
                .contact(new Contact().name("khipu").url(p.portalUrl()))
                .description("""
                        API de facturación electrónica SUNAT (Perú) para integradores: emite, firma, envía y consulta comprobantes
                        electrónicos UBL 2.1 sin implementar el estándar ni el protocolo SOAP de SUNAT.

                        ## Autenticación
                        Cada petición de integración lleva la cabecera `X-Api-Key: fk_…` de la empresa emisora (se crea en el portal,
                        sección *API keys*; el secreto se muestra una sola vez). Una llave pertenece a una única empresa (RUC), por lo
                        que no hace falta indicar el RUC en las llamadas. Las rutas de *Autenticación (portal)* y *Cuenta y empresas*
                        son exclusivas del portal web (sesión JWT). Los *Catálogos SUNAT* son públicos.

                        ## Entornos
                        Cada empresa está en `BETA` (homologación de SUNAT: los comprobantes **no tienen validez tributaria**, ideal para
                        integrar) o en `PRODUCCION`. La URL de la API es la misma; el entorno lo decide la configuración de la empresa.

                        ## Sobre de respuesta
                        Todas las respuestas JSON tienen la forma `{"estado": "exito" | "error", "datos": …, "codigo": …, "mensaje": …, "errores": …}`.
                        En éxito, `datos` trae el recurso; en error, `codigo` es un identificador estable (p. ej. `VALIDACION`, `NO_ENCONTRADO`,
                        `DUPLICADO`, `FORMA_PAGO_INVALIDA`), `mensaje` es legible y, cuando la regla viene de SUNAT, empieza por su código
                        (`3319 - La suma de las cuotas…`). `errores` detalla los campos inválidos en `VALIDACION`. Los nombres de campo van en
                        `snake_case`.

                        ## Estados del comprobante
                        `RECIBIDO` (validado y numerado) → `FIRMADO` (XML firmado; se envía en la misma llamada salvo `enviar_automatico: false`) →
                        `ENVIADO` → `ACEPTADO` (CDR con código 0) · `ACEPTADO_CON_OBS` (aceptado, observaciones 4xxx: revíselas) ·
                        `RECHAZADO` (SUNAT lo rechazó: corrija y vuelva a emitir; el número puede reutilizarse).
                        `ERROR_ENVIO`: SUNAT no estuvo disponible; khipu reintenta con espera creciente hasta %d veces (~6 h entre intentos al final)
                        y puede forzarse con `POST /v1/facturas/{id}/enviar`. `INVALIDO`: el XML no pasó la validación local. `ANULADO`: baja aceptada.

                        ## Códigos HTTP
                        `201` creado · `200` ok · `204` sin contenido · `400` parámetro o JSON mal formado · `401` sin credenciales válidas ·
                        `403` operación reservada al portal · `404` no existe (o es de otra empresa) · `409` conflicto (duplicado, estado no enviable) ·
                        `422` datos inválidos o regla de negocio · `500` error interno (incluye `trace_id`).

                        ## Plazos SUNAT
                        Una factura debe llegar a SUNAT dentro de los **3 días calendario** siguientes a su emisión. Emita el mismo día y vigile
                        los `ERROR_ENVIO`.

                        ## Códigos y catálogos
                        Los valores de `tipo_afectacion_igv`, `unidad`, `tipo_operacion`, `tipo_doc`, etc. son códigos oficiales de SUNAT:
                        consúltelos con `GET /v1/catalogos` (públicos) o en la sección *Catálogos* del developer portal.
                        """.formatted(p.outbox().maxIntentos())));
    }

    /**
     * /v1/empresas (plural) es cuenta-scoped vía JWT (CuentaActual) — una X-Api-Key no llega a
     * setear eso, así que NO se marca ahí aunque el prefijo se parezca. Solo las rutas realmente
     * scoped a tenant (ApiKeyFilter → TenantActual) aceptan X-Api-Key.
     */
    @Bean GlobalOpenApiCustomizer apiKeySecurityCustomizer() {
        List<String> conApiKey = List.of("/v1/empresa", "/v1/series", "/v1/facturas", "/v1/notas", "/v1/bajas");
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            openApi.getComponents().addSecuritySchemes("ApiKey",
                    new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Api-Key"));
            SecurityRequirement requerimiento = new SecurityRequirement().addList("ApiKey");
            openApi.getPaths().forEach((ruta, item) -> {
                boolean aplica = conApiKey.stream().anyMatch(p -> ruta.equals(p) || ruta.startsWith(p + "/"));
                if (aplica) item.readOperations().forEach(op -> op.addSecurityItem(requerimiento));
            });
        };
    }

    /**
     * Debe correr ANTES que PlatformKeyFilter/JwtFilter/ApiKeyFilter (órdenes 5/8/10): CorsFilter
     * responde el preflight OPTIONS directo (sin seguir la cadena) cuando corresponde, así que si
     * corriera después, ApiKeyFilter rechazaría el preflight con 401 antes de que CORS actúe.
     */
    @Bean FilterRegistrationBean<CorsFilter> corsFilter(AppProperties p) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(p.portalUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Api-Key", "Authorization", "X-Empresa"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", config);
        var f = new FilterRegistrationBean<>(new CorsFilter(source));
        f.addUrlPatterns("/v1/*");
        f.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return f;
    }

    @Bean Clock clock(AppProperties p) { return Clock.system(ZoneId.of(p.zonaHoraria())); }
    @Bean SecretCipher secretCipher(AppProperties p) { exigirSecretosDePlataforma(p); return new AesGcmSecretCipher(p.masterKey()); }
    @Bean UnitOfWork unitOfWork(PlatformTransactionManager tm) { return new JdbcUnitOfWork(tm); }

    @Bean TenantRepository tenantRepository(JdbcTemplate jdbc, SecretCipher c) { return new JdbcTenantRepository(jdbc, c); }
    @Bean ApiKeyRepository apiKeyRepository(JdbcTemplate jdbc) { return new JdbcApiKeyRepository(jdbc); }
    @Bean SerieRepository serieRepository(JdbcTemplate jdbc) { return new JdbcSerieRepository(jdbc); }
    @Bean EstablecimientoRepository establecimientoRepository(JdbcTemplate jdbc) { return new JdbcEstablecimientoRepository(jdbc); }
    @Bean EmisorDeSerieRepository emisorDeSerieRepository(JdbcTemplate jdbc) { return new JdbcEmisorDeSerieRepository(jdbc); }
    @Bean ComprobanteRepository comprobanteRepository(JdbcTemplate jdbc) { return new JdbcComprobanteRepository(jdbc); }
    @Bean BajaRepository bajaRepository(JdbcTemplate jdbc) { return new JdbcBajaRepository(jdbc); }
    @Bean OutboxRepository outboxRepository(JdbcTemplate jdbc) { return new JdbcOutboxRepository(jdbc); }
    @Bean CuentaRepository cuentaRepository(JdbcTemplate jdbc) { return new JdbcCuentaRepository(jdbc); }
    @Bean UsuarioRepository usuarioRepository(JdbcTemplate jdbc) { return new JdbcUsuarioRepository(jdbc); }
    @Bean SesionRepository sesionRepository(JdbcTemplate jdbc) { return new JdbcSesionRepository(jdbc); }

    @Bean DocumentStorage documentStorage(AppProperties p) { return new FileSystemDocumentStorage(Path.of(p.storage().fsRoot())); }
    @Bean UblGenerator ublGenerator() { return new FreemarkerUblGenerator(); }
    @Bean XsdValidator xsdValidator() { return new JaxpXsdValidator(); }
    @Bean XmlSigner xmlSigner() { return new XmlDsigSigner(); }
    @Bean CdrParser cdrParser() { return new XmlCdrParser(); }
    @Bean SunatBillingGateway sunatBillingGateway(AppProperties p) {
        return new SoapBillingGateway(new SunatUrls(p.sunat().betaUrl(), p.sunat().prodUrl()), Duration.ofSeconds(p.sunat().timeoutSeconds()));
    }
    @Bean SunatConsultaGateway sunatConsultaGateway(AppProperties p) {
        return new SoapConsultaGateway(p.sunat().consultaUrl(), p.sunat().consultaBetaUrl(), p.sunat().validezUrl(), p.sunat().validezBetaUrl(), Duration.ofSeconds(p.sunat().timeoutSeconds()));
    }
    @Bean RecuperarCdrUseCase recuperarCdr(ComprobanteRepository c, TenantRepository t, DocumentStorage s, SunatConsultaGateway g, CdrParser cdr, UnitOfWork u) {
        return new RecuperarCdrService(c, t, s, g, cdr, u);
    }
    @Bean ConsultarValidezUseCase consultarValidez(TenantRepository t, SunatConsultaGateway g) { return new ConsultarValidezService(t, g); }
    @Bean RecuperarCdrWorker recuperarCdrWorker(RecuperarCdrUseCase cdrs) { return new RecuperarCdrWorker(cdrs); }

    @Bean EnviarDocumentoUseCase enviarDocumento(ComprobanteRepository c, TenantRepository t, DocumentStorage s, SunatBillingGateway g, CdrParser p,
                                                OutboxRepository o, UnitOfWork u, Clock clock) {
        return new EnviarDocumentoService(c, t, s, g, p, o, u, clock);
    }
    @Bean EmitirComprobanteUseCase emitirComprobante(ComprobanteRepository c, SerieRepository se, TenantRepository t, DocumentStorage s,
                                                    UblGenerator ubl, XsdValidator xsd, XmlSigner signer, EnviarDocumentoUseCase enviar, UnitOfWork u, Clock clock, EmisorDeSerieRepository emisor) {
        return new EmitirComprobanteService(c, se, t, s, ubl, xsd, signer, enviar, u, clock, emisor);
    }
    @Bean PdfGenerator pdfGenerator() { return new FlyingSaucerPdfGenerator(); }
    @Bean PersonalizarPdfUseCase personalizarPdf(TenantRepository t, DocumentStorage s, PdfGenerator pdf, Clock clock) { return new PersonalizarPdfService(t, s, pdf, clock); }
    @Bean ConsultarComprobanteUseCase consultarComprobante(ComprobanteRepository c, TenantRepository t, DocumentStorage s, PdfGenerator pdf, EmisorDeSerieRepository emisor) {
        return new ConsultarComprobanteService(c, t, s, pdf, emisor);
    }
    @Bean CompartirComprobanteUseCase compartirComprobante(ConsultarComprobanteUseCase consultar, TenantRepository t, CorreoSender correo) {
        return new CompartirComprobanteService(consultar, t, correo);
    }
    @Bean AdministrarTenantUseCase administrarTenant(TenantRepository t, SerieRepository s, ApiKeyRepository k, UnitOfWork u, AppProperties p, Clock clock, EstablecimientoRepository est) {
        return new AdministrarTenantService(t, s, k, u, p.apiKeyPepper(), clock, est);
    }

    @Bean PasswordHasher passwordHasher() { return new BcryptPasswordHasher(); }
    @Bean TokenEmisor tokenEmisor(AppProperties p) { return new JwtTokenEmisor(p.jwtSecret()); }
    /** Sin app.mail.habilitado=true (MAIL_HABILITADO), los correos se escriben en el log en lugar de enviarse. */
    @Bean CorreoSender correoSender(AppProperties p, ObjectProvider<JavaMailSender> mailSenderProvider) {
        if (!p.mail().habilitado()) return new LogCorreoSender();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) throw new IllegalStateException("app.mail.habilitado=true pero no hay JavaMailSender configurado (revisa MAIL_HOST)");
        return new SmtpCorreoSender(mailSender, p.mail().remitente());
    }

    @Bean AutenticarUsuarioUseCase autenticarUsuario(CuentaRepository cu, UsuarioRepository us, SesionRepository se, PasswordHasher h,
                                                    TokenEmisor te, CorreoSender co, UnitOfWork u, Clock clock) {
        return new AutenticarUsuarioService(cu, us, se, h, te, co, u, clock);
    }
    @Bean GestionarEmpresasUseCase gestionarEmpresas(TenantRepository t, CuentaRepository cu, UnitOfWork u) {
        return new GestionarEmpresasService(t, cu, u);
    }

    @Bean DarDeBajaUseCase darDeBaja(BajaRepository b, ComprobanteRepository c, TenantRepository t, DocumentStorage s, UblGenerator ubl, XsdValidator xsd, XmlSigner signer,
                                     SunatBillingGateway g, CdrParser p, OutboxRepository o, UnitOfWork u, Clock clock) {
        return new DarDeBajaService(b, c, t, s, ubl, xsd, signer, g, p, o, u, clock);
    }
    @Bean OutboxWorker outboxWorker(OutboxRepository o, UnitOfWork u, EnviarDocumentoUseCase e, DarDeBajaUseCase b, Clock clock, AppProperties p) {
        return new OutboxWorker(o, u, e, b, clock, p.outbox().maxIntentos());
    }
    @Bean ControlarPlazoEnvioUseCase controlarPlazoEnvio(ComprobanteRepository c, UnitOfWork u, Clock clock) { return new ControlarPlazoEnvioService(c, u, clock); }
    @Bean PlazoEnvioWorker plazoEnvioWorker(ControlarPlazoEnvioUseCase plazos) { return new PlazoEnvioWorker(plazos); }

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
    @Bean FilterRegistrationBean<JwtFilter> jwtFilter(TokenEmisor te, TenantRepository t) {
        var f = new FilterRegistrationBean<>(new JwtFilter(te, t));
        f.addUrlPatterns("/v1/*"); f.setOrder(8); return f;
    }
}
