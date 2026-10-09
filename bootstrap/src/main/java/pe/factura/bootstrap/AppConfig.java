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
import org.springframework.beans.factory.annotation.Value;
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
import pe.factura.adapters.crypto.JwtAdministradorTokenEmisor;
import pe.factura.adapters.crypto.JwtTokenEmisor;
import pe.factura.adapters.crypto.TotpRfc6238;
import pe.factura.adapters.mail.LogCorreoSender;
import pe.factura.adapters.mail.SmtpCorreoSender;
import pe.factura.adapters.persistence.*;
import pe.factura.adapters.rest.AdminAuthFilter;
import pe.factura.adapters.rest.ApiKeyFilter;
import pe.factura.adapters.rest.JwtFilter;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.scheduler.AplicarCambiosDePlanWorker;
import pe.factura.adapters.scheduler.LimpiezaIdempotenciaWorker;
import pe.factura.adapters.scheduler.LimpiezaIntentosDeAccesoWorker;
import pe.factura.adapters.scheduler.PlazoEnvioWorker;
import pe.factura.adapters.signing.XmlDsigSigner;
import pe.factura.adapters.storage.FileSystemDocumentStorage;
import pe.factura.adapters.storage.S3DocumentStorage;
import pe.factura.adapters.scheduler.IntegridadWorker;
import pe.factura.adapters.sunat.SoapBillingGateway;
import pe.factura.adapters.sunat.SunatUrls;
import pe.factura.adapters.sunat.SoapConsultaGateway;
import pe.factura.adapters.sunat.SondeoDeSunatHttp;
import pe.factura.adapters.scheduler.RecuperarCdrWorker;
import pe.factura.adapters.sunat.XmlCdrParser;
import pe.factura.adapters.pdf.FlyingSaucerPdfGenerator;
import pe.factura.adapters.pdf.ZxingCodigoQr;
import pe.factura.adapters.ubl.FreemarkerUblGenerator;
import pe.factura.adapters.ubl.XmlEmisorFirmado;
import pe.factura.adapters.ubl.JaxpXsdValidator;
import pe.factura.application.port.in.*;
import pe.factura.application.port.out.*;
import pe.factura.application.service.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

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

    /**
     * Vida de la sesión del administrador (#177): entre 5 y 60 minutos. Ve los datos fiscales de todos los clientes, así que su sesión
     * es siempre más corta que la del cliente (15 min renovables por 30 días); un valor fuera de rango aborta el arranque en vez de
     * dejar pasar una sesión de un día por un cero de más.
     */
    static Duration vidaSesionAdmin(int minutos) {
        if (minutos < 5 || minutos > 60)
            throw new IllegalStateException("ADMIN_SESION_MINUTOS debe estar entre 5 y 60 (es " + minutos + ")");
        return Duration.ofMinutes(minutos);
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
     * Debe correr ANTES que AdminAuthFilter/JwtFilter/ApiKeyFilter (órdenes 5/8/10): CorsFilter
     * responde el preflight OPTIONS directo (sin seguir la cadena) cuando corresponde, así que si
     * corriera después, ApiKeyFilter rechazaría el preflight con 401 antes de que CORS actúe.
     */
    @Bean FilterRegistrationBean<CorsFilter> corsFilter(AppProperties p) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(p.portalUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Api-Key", "Authorization", "X-Empresa", "Idempotency-Key"));
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
    @Bean VerificacionCorreoRepository verificacionCorreoRepository(JdbcTemplate jdbc) { return new JdbcVerificacionCorreoRepository(jdbc); }
    @Bean SesionRepository sesionRepository(JdbcTemplate jdbc) { return new JdbcSesionRepository(jdbc); }
    @Bean AdministradorRepository administradorRepository(JdbcTemplate jdbc) { return new JdbcAdministradorRepository(jdbc); }
    @Bean AuditoriaAdminRepository auditoriaAdminRepository(JdbcTemplate jdbc) { return new JdbcAuditoriaAdminRepository(jdbc); }
    @Bean SegundoFactorRepository segundoFactorRepository(JdbcTemplate jdbc) { return new JdbcSegundoFactorRepository(jdbc); }
    @Bean CuentasAdminRepository cuentasAdminRepository(JdbcTemplate jdbc) { return new JdbcCuentasAdminRepository(jdbc); }
    @Bean EmpresasAdminRepository empresasAdminRepository(JdbcTemplate jdbc) { return new JdbcEmpresasAdminRepository(jdbc); }
    @Bean SuspensionRepository suspensionRepository(JdbcTemplate jdbc) { return new JdbcSuspensionRepository(jdbc); }
    @Bean BajaDeCuentaRepository bajaDeCuentaRepository(JdbcTemplate jdbc) { return new JdbcBajaDeCuentaRepository(jdbc); }
    @Bean AccionesDeEmpresaRepository accionesDeEmpresaRepository(JdbcTemplate jdbc) { return new JdbcAccionesDeEmpresaRepository(jdbc); }
    @Bean AccesosDeSoporteRepository accesosDeSoporteRepository(JdbcTemplate jdbc) { return new JdbcAccesosDeSoporteRepository(jdbc); }
    @Bean PlanRepository planRepository(JdbcTemplate jdbc) { return new JdbcPlanRepository(jdbc); }
    @Bean SuscripcionRepository suscripcionRepository(JdbcTemplate jdbc) { return new JdbcSuscripcionRepository(jdbc); }
    @Bean ConsumoRepository consumoRepository(JdbcTemplate jdbc) { return new JdbcConsumoRepository(jdbc); }
    @Bean ConsumoPorCuentaRepository consumoPorCuentaRepository(JdbcTemplate jdbc) { return new JdbcConsumoPorCuentaRepository(jdbc); }
    @Bean PagoRepository pagoRepository(JdbcTemplate jdbc) { return new JdbcPagoRepository(jdbc); }
    @Bean ResumenDeComprobantesRepository resumenDeComprobantesRepository(JdbcTemplate jdbc) { return new JdbcResumenDeComprobantesRepository(jdbc); }
    @Bean MonitorDeEmisionRepository monitorDeEmisionRepository(JdbcTemplate jdbc) { return new JdbcMonitorDeEmisionRepository(jdbc); }

    @Bean DocumentStorage documentStorage(AppProperties p) {
        AppProperties.Storage st = p.storage();
        if ("s3".equalsIgnoreCase(st.type())) {
            AppProperties.Storage.S3 s3 = st.s3();
            return S3DocumentStorage.crear(s3.endpoint(), s3.region(), s3.accessKey(), s3.secretKey(), s3.bucket(), s3.pathStyle());
        }
        if (!"fs".equalsIgnoreCase(st.type())) throw new IllegalStateException("STORAGE_TYPE debe ser fs o s3, no " + st.type());
        return new FileSystemDocumentStorage(Path.of(st.fsRoot()));
    }
    @Bean VerificarIntegridadUseCase verificarIntegridad(ComprobanteRepository c, DocumentStorage s) { return new VerificarIntegridadService(c, s); }
    @Bean IntegridadWorker integridadWorker(VerificarIntegridadUseCase v, Clock clock, AppProperties p) { return new IntegridadWorker(v, clock, p.integridad() == null ? 7 : p.integridad().dias()); }
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
    /** Sondea el WSDL de cada servicio de SUNAT (sin credenciales ni comprobantes) y guarda la lectura 30 s para que el monitor, que se refresca solo, no la llame cada vez. */
    @Bean SondeoDeSunat sondeoDeSunat(AppProperties p, Clock clock) {
        AppProperties.Sunat s = p.sunat();
        return new SondeoDeSunatHttp(Map.of(SondeoDeSunat.Servicio.ENVIO_PRODUCCION, nulo(s.prodUrl()), SondeoDeSunat.Servicio.ENVIO_BETA, nulo(s.betaUrl()),
                SondeoDeSunat.Servicio.CONSULTA_DE_CDR, nulo(s.consultaUrl()), SondeoDeSunat.Servicio.CONSULTA_DE_VALIDEZ, nulo(s.validezUrl())),
                Duration.ofSeconds(Math.min(s.timeoutSeconds(), 5)), Duration.ofSeconds(30), clock);
    }
    private static String nulo(String url) { return url == null ? "" : url; }
    @Bean ColaDeErroresRepository colaDeErroresRepository(JdbcTemplate jdbc) { return new JdbcColaDeErroresRepository(jdbc); }
    @Bean ConsultarColaDeErroresUseCase consultarColaDeErrores(ColaDeErroresRepository cola) { return new ConsultarColaDeErroresService(cola); }
    @Bean ResolverErroresUseCase resolverErrores(ColaDeErroresRepository cola, EnviarDocumentoUseCase enviar, RecuperarCdrUseCase cdrs, ComprobanteRepository comprobantes, OutboxRepository outbox,
                                                 TenantRepository tenants, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new ResolverErroresService(cola, enviar, cdrs, comprobantes, outbox, tenants, auditoria, u, clock);
    }
    @Bean ConsultarComprobanteAdminUseCase consultarComprobanteAdmin(ColaDeErroresRepository cola, ComprobanteRepository comprobantes, TenantRepository tenants,
                                                                     DocumentStorage storage) {
        return new ConsultarComprobanteAdminService(cola, comprobantes, tenants, storage);
    }
    @Bean AvisosRepository avisosRepository(JdbcTemplate jdbc) { return new JdbcAvisosRepository(jdbc); }
    @Bean ConsultarAvisosUseCase consultarAvisos(AvisosRepository avisos, Clock clock) { return new ConsultarAvisosService(avisos, clock); }
    @Bean AvisarAlClienteUseCase avisarAlCliente(AvisosRepository avisos, CorreoSender correo, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock, PlantillasDeCorreo plantillas) {
        return new AvisarAlClienteService(avisos, correo, auditoria, u, clock, plantillas);
    }
    @Bean RemitenteRepository remitenteRepository(JdbcTemplate jdbc) { return new JdbcConfiguracionDePlataforma.Remitente(jdbc); }
    @Bean PlantillasRepository plantillasRepository(JdbcTemplate jdbc) { return new JdbcConfiguracionDePlataforma.Plantillas(jdbc); }
    @Bean BannerRepository bannerRepository(JdbcTemplate jdbc) { return new JdbcConfiguracionDePlataforma.Banner(jdbc); }
    @Bean PlantillasDeCorreo plantillasDeCorreo(PlantillasRepository plantillas) { return new PlantillasDeCorreo(plantillas); }
    /** El remitente de la configuración del servidor (`MAIL_REMITENTE`): el de siempre mientras ningún administrador fije otro. */
    @Bean RemitenteDeCorreo remitentePredeterminado(AppProperties p) { return remitenteDeLaConfiguracion(p.mail().remitente()); }
    @Bean ConfigurarPlataformaUseCase configurarPlataforma(RemitenteRepository remitentes, PlantillasRepository plantillas, BannerRepository banners, AuditoriaAdminRepository auditoria,
                                                           UnitOfWork u, Clock clock, RemitenteDeCorreo remitentePredeterminado) {
        return new ConfigurarPlataformaService(remitentes, plantillas, banners, auditoria, u, clock, remitentePredeterminado);
    }
    @Bean ConsultarBannerUseCase consultarBanner(BannerRepository banners, Clock clock) { return new ConsultarBannerService(banners, clock); }
    /** Los planes de la página de precios (H20): público, lo lee la portada. */
    @Bean ConsultarPlanesPublicadosUseCase consultarPlanesPublicados(PlanRepository planes, Clock clock) { return new ConsultarPlanesPublicadosService(planes, clock); }
    @Bean MonitorearEmisionUseCase monitorearEmision(MonitorDeEmisionRepository m, SondeoDeSunat s, Clock clock) { return new MonitorDeEmisionService(m, s, clock); }
    @Bean RecuperarCdrUseCase recuperarCdr(ComprobanteRepository c, TenantRepository t, DocumentStorage s, SunatConsultaGateway g, CdrParser cdr, UnitOfWork u) {
        return new RecuperarCdrService(c, t, s, g, cdr, u);
    }
    @Bean ConsultarValidezUseCase consultarValidez(TenantRepository t, SunatConsultaGateway g) { return new ConsultarValidezService(t, g); }
    @Bean RecuperarCdrWorker recuperarCdrWorker(RecuperarCdrUseCase cdrs) { return new RecuperarCdrWorker(cdrs); }

    @Bean EnviarDocumentoUseCase enviarDocumento(ComprobanteRepository c, TenantRepository t, DocumentStorage s, SunatBillingGateway g, CdrParser p,
                                                OutboxRepository o, UnitOfWork u, Clock clock, RechazoDeSolRepository rechazos) {
        return new EnviarDocumentoService(c, t, s, g, p, o, u, clock, rechazos);
    }
    @Bean RechazoDeSolRepository rechazoDeSolRepository(JdbcTemplate jdbc) { return new JdbcRechazoDeSolRepository(jdbc); }
    @Bean EmitirComprobanteUseCase emitirComprobante(ComprobanteRepository c, SerieRepository se, TenantRepository t, DocumentStorage s,
                                                    UblGenerator ubl, XsdValidator xsd, XmlSigner signer, EnviarDocumentoUseCase enviar, UnitOfWork u, Clock clock, EmisorDeSerieRepository emisor,
                                                    BajaRepository bajas, IdempotenciaRepository idempotencia) {
        return new EmitirComprobanteService(c, se, t, s, ubl, xsd, signer, enviar, u, clock, emisor, bajas, idempotencia);
    }
    @Bean IdempotenciaRepository idempotenciaRepository(JdbcTemplate jdbc) { return new JdbcIdempotenciaRepository(jdbc); }
    @Bean LimpiarIdempotenciaUseCase limpiarIdempotencia(IdempotenciaRepository i, Clock clock) { return new LimpiarIdempotenciaService(i, clock); }
    @Bean LimpiezaIdempotenciaWorker limpiezaIdempotenciaWorker(LimpiarIdempotenciaUseCase l) { return new LimpiezaIdempotenciaWorker(l); }
    @Bean PdfGenerator pdfGenerator() { return new FlyingSaucerPdfGenerator(); }
    @Bean PersonalizarPdfUseCase personalizarPdf(TenantRepository t, DocumentStorage s, PdfGenerator pdf, Clock clock) { return new PersonalizarPdfService(t, s, pdf, clock); }
    @Bean EmisorFirmado emisorFirmado() { return new XmlEmisorFirmado(); }
    @Bean ConsultarComprobanteUseCase consultarComprobante(ComprobanteRepository c, TenantRepository t, DocumentStorage s, PdfGenerator pdf, EmisorDeSerieRepository emisor, EmisorFirmado firmado) {
        return new ConsultarComprobanteService(c, t, s, pdf, emisor, firmado);
    }
    @Bean CompartirComprobanteUseCase compartirComprobante(ConsultarComprobanteUseCase consultar, TenantRepository t, CorreoSender correo) {
        return new CompartirComprobanteService(consultar, t, correo);
    }
    @Bean AdministrarTenantUseCase administrarTenant(TenantRepository t, SerieRepository s, ApiKeyRepository k, UnitOfWork u, AppProperties p, Clock clock, EstablecimientoRepository est,
                                                    AuditoriaAdminRepository auditoria, RechazoDeSolRepository rechazos) {
        return new AdministrarTenantService(t, s, k, u, p.apiKeyPepper(), clock, est, auditoria, rechazos);
    }

    @Bean PasswordHasher passwordHasher() { return new BcryptPasswordHasher(); }
    @Bean TokenEmisor tokenEmisor(AppProperties p) { return new JwtTokenEmisor(p.jwtSecret()); }
    @Bean AdministradorTokenEmisor administradorTokenEmisor(AppProperties p, @Value("${app.admin-sesion-minutos:30}") int minutos) {
        return new JwtAdministradorTokenEmisor(p.jwtSecret(), vidaSesionAdmin(minutos));
    }
    @Bean SegundoFactor segundoFactor() { return new TotpRfc6238(); }
    @Bean CodigoQr codigoQr() { return new ZxingCodigoQr(); }
    /** Sin app.mail.habilitado=true (MAIL_HABILITADO), los correos se escriben en el log en lugar de enviarse. */
    @Bean CorreoSender correoSender(AppProperties p, ObjectProvider<JavaMailSender> mailSenderProvider, @Value("${spring.mail.host:}") String mailHost,
                                    RemitenteRepository remitentes, RemitenteDeCorreo remitentePredeterminado) {
        if (!p.mail().habilitado()) return new LogCorreoSender();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        // No alcanza con que exista el bean: Spring lo crea igual con `spring.mail.host` vacío, y entonces cada correo
        // falla en tiempo de ejecución (recuperación de contraseña, comprobantes al cliente) en vez de avisar al arrancar.
        if (mailSender == null || mailHost.isBlank())
            throw new IllegalStateException("app.mail.habilitado=true pero no hay SMTP configurado (define MAIL_HOST)");
        return new SmtpCorreoSender(mailSender, () -> remitenteVigente(remitentes, remitentePredeterminado));
    }

    /**
     * El remitente que un administrador fijó, o el de la configuración. Un correo no deja de salir porque la base no respondió al leer el remitente: sale con el de la configuración.
     */
    static RemitenteDeCorreo remitenteVigente(RemitenteRepository remitentes, RemitenteDeCorreo predeterminado) {
        java.util.Optional<RemitenteRepository.Guardado> guardado;
        try {
            guardado = remitentes.buscar();
        } catch (RuntimeException e) {
            return predeterminado;
        }
        return guardado.map(RemitenteRepository.Guardado::remitente).orElse(predeterminado);
    }

    /**
     * `MAIL_REMITENTE` se valida como el remitente de un administrador. Si el valor era uno que antes se aceptaba y ahora no (por ejemplo «khipu &lt;no-responder@khipu.pe&gt;»), se
     * conserva tal cual en vez de impedir el arranque: el servidor de correo lo interpretaba así.
     */
    static RemitenteDeCorreo remitenteDeLaConfiguracion(String configurado) {
        try {
            return RemitenteDeCorreo.de(null, configurado, null);
        } catch (DomainException e) {
            return new RemitenteDeCorreo(null, configurado, null);
        }
    }

    @Bean IntentosDeAccesoRepository intentosDeAccesoRepository(JdbcTemplate jdbc) { return new JdbcIntentosDeAccesoRepository(jdbc); }
    @Bean LimiteDeIntentos limiteDeIntentos(IntentosDeAccesoRepository i, Clock clock) { return new LimiteDeIntentos(i, clock); }
    @Bean LimpiezaIntentosDeAccesoWorker limpiezaIntentosDeAccesoWorker(LimiteDeIntentos l) { return new LimpiezaIntentosDeAccesoWorker(l); }

    @Bean AutenticarUsuarioUseCase autenticarUsuario(CuentaRepository cu, UsuarioRepository us, SesionRepository se, PasswordHasher h,
                                                    TokenEmisor te, CorreoSender co, UnitOfWork u, Clock clock, VerificacionCorreoRepository v,
                                                    SuspensionRepository suspensiones, PlantillasDeCorreo plantillas, LimiteDeIntentos limite) {
        return new AutenticarUsuarioService(cu, us, se, h, te, co, u, clock, v, suspensiones, plantillas, limite);
    }
    @Bean GestionarEmpresasUseCase gestionarEmpresas(TenantRepository t, CuentaRepository cu, UnitOfWork u) {
        return new GestionarEmpresasService(t, cu, u);
    }

    @Bean AutenticarAdministradorUseCase autenticarAdministrador(AdministradorRepository a, PasswordHasher h, AdministradorTokenEmisor te,
                                                              SegundoFactorRepository f, SegundoFactor totp, SecretCipher c, CodigoQr qr, UnitOfWork u,
                                                              AuditoriaAdminRepository auditoria, Clock clock, LimiteDeIntentos limite) {
        return new AutenticarAdministradorService(a, h, te, f, totp, c, qr, u, auditoria, clock, limite);
    }
    @Bean ListarCuentasAdminUseCase listarCuentasAdmin(CuentasAdminRepository cuentas) { return new ListarCuentasAdminService(cuentas); }
    @Bean DetalleCuentaAdminUseCase detalleCuentaAdmin(CuentasAdminRepository cuentas) { return new DetalleCuentaAdminService(cuentas); }
    @Bean ListarEmpresasAdminUseCase listarEmpresasAdmin(EmpresasAdminRepository empresas, Clock clock) { return new ListarEmpresasAdminService(empresas, clock); }
    @Bean DetalleEmpresaAdminUseCase detalleEmpresaAdmin(EmpresasAdminRepository empresas, Clock clock) { return new DetalleEmpresaAdminService(empresas, clock); }
    @Bean SuspenderCuentaUseCase suspenderCuenta(CuentaRepository cuentas, SuspensionRepository suspensiones, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock,
                                                 BajaDeCuentaRepository bajas) {
        return new SuspenderCuentaService(cuentas, suspensiones, auditoria, u, clock, bajas);
    }
    @Bean DarDeBajaCuentaUseCase darDeBajaCuenta(CuentaRepository cuentas, BajaDeCuentaRepository bajas, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new DarDeBajaCuentaService(cuentas, bajas, auditoria, u, clock);
    }
    @Bean ConsultarConsumoUseCase consultarConsumo(ConsumoRepository consumos, CuentaRepository cuentas, TenantRepository tenants, Clock clock) {
        return new ConsultarConsumoService(consumos, cuentas, tenants, clock);
    }
    @Bean ResumirComprobantesUseCase resumirComprobantes(ResumenDeComprobantesRepository resumenes) { return new ResumirComprobantesService(resumenes); }
    @Bean ConsultarConsumoDeCuentasUseCase consultarConsumoDeCuentas(ConsumoPorCuentaRepository consumos, Clock clock) { return new ConsultarConsumoDeCuentasService(consumos, clock); }
    /** Un solo servicio para las dos caras del cambio de plan: lo que hacen los administradores y lo que aplica el trabajo programado al llegar la fecha. */
    @Bean CambiarPlanDeCuentaService cambiarPlanDeCuenta(PlanRepository planes, SuscripcionRepository suscripciones, ConsultarConsumoUseCase consumo,
                                                        AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new CambiarPlanDeCuentaService(planes, suscripciones, consumo, auditoria, u, clock);
    }
    /** Un solo servicio para registrar un pago a mano y consultar el historial (#194). */
    @Bean PagosDeCuentaService pagosDeCuenta(PagoRepository pagos, SuscripcionRepository suscripciones, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new PagosDeCuentaService(pagos, suscripciones, auditoria, u, clock);
    }
    @Bean AplicarCambiosDePlanWorker aplicarCambiosDePlanWorker(AplicarCambiosDePlanUseCase cambios) { return new AplicarCambiosDePlanWorker(cambios); }
    @Bean GestionarPlanesUseCase gestionarPlanes(PlanRepository planes, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new GestionarPlanesService(planes, auditoria, u, clock);
    }
    @Bean AccionesDeEmpresaUseCase accionesDeEmpresa(TenantRepository tenants, AccionesDeEmpresaRepository acciones, SunatBillingGateway sunat, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new AccionesDeEmpresaService(tenants, acciones, sunat, auditoria, u, clock);
    }
    @Bean ImpersonarUsuarioUseCase impersonarUsuario(UsuarioRepository usuarios, TokenEmisor tokens, AuditoriaAdminRepository auditoria, UnitOfWork u, Clock clock) {
        return new ImpersonarUsuarioService(usuarios, tokens, auditoria, u, clock);
    }
    @Bean AccesosDeSoporteUseCase accesosDeSoporte(AccesosDeSoporteRepository registros) { return new AccesosDeSoporteService(registros); }
    @Bean ConsultarMiCuentaUseCase consultarMiCuenta(CuentaRepository cuentas, CambiarPlanDeCuentaUseCase planes, ConsultarConsumoUseCase consumo) {
        return new ConsultarMiCuentaService(cuentas, planes, consumo);
    }
    @Bean SoporteDeAccesoUseCase soporteDeAcceso(UsuarioRepository usuarios, SesionRepository sesiones, VerificacionCorreoRepository verificaciones, CorreoSender correo,
                                                 UnitOfWork u, AuditoriaAdminRepository auditoria, Clock clock, PlantillasDeCorreo plantillas) {
        return new SoporteDeAccesoService(usuarios, sesiones, verificaciones, correo, u, auditoria, clock, plantillas);
    }
    @Bean CrearAdministradorUseCase crearAdministrador(AdministradorRepository a, PasswordHasher h, UnitOfWork u, AuditoriaAdminRepository auditoria, Clock clock) {
        return new CrearAdministradorService(a, h, u, auditoria, clock);
    }
    @Bean AltaAsistidaUseCase altaAsistida(CuentaRepository cu, UsuarioRepository us, SesionRepository se, TenantRepository t, SerieRepository s, ApiKeyRepository k,
                                          PasswordHasher h, CorreoSender co, UnitOfWork u, AuditoriaAdminRepository auditoria, AppProperties p, Clock clock,
                                          IdempotenciaRepository idempotencia, SecretCipher cifrador, PlantillasDeCorreo plantillas) {
        return new AltaAsistidaService(cu, us, se, t, s, k, h, co, u, auditoria, p.apiKeyPepper(), clock, idempotencia, cifrador, plantillas);
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
    @Bean FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ApiKeyRepository k, AppProperties p, SuspensionRepository suspensiones) {
        exigirSecretosDePlataforma(p);
        var f = new FilterRegistrationBean<>(new ApiKeyFilter(k, p.apiKeyPepper(), suspensiones));
        f.addUrlPatterns("/v1/*"); f.setOrder(10); return f;
    }
    @Bean FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(AppProperties p, AdministradorTokenEmisor te) {
        var f = new FilterRegistrationBean<>(new AdminAuthFilter(p.platformAdminKey(), te));
        f.addUrlPatterns("/v1/*"); f.setOrder(5); return f;
    }
    @Bean FilterRegistrationBean<JwtFilter> jwtFilter(TokenEmisor te, TenantRepository t, UsuarioRepository us, SuspensionRepository suspensiones) {
        var f = new FilterRegistrationBean<>(new JwtFilter(te, t, us, suspensiones));
        f.addUrlPatterns("/v1/*"); f.setOrder(8); return f;
    }
}
