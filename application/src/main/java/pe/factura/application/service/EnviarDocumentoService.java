package pe.factura.application.service;

import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.InformarEnResumenUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultsSunat;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class EnviarDocumentoService implements EnviarDocumentoUseCase {
    public static final String ACCION_ENVIAR = "ENVIAR";
    /**
     * LOG-01: una reserva de envío ({@link ComprobanteRepository#reservarEnvio}) sin resultado guardado por más de esto es de un proceso que murió entre el sendBill y
     * el guardado; otro envío puede retomarla. Holgado frente al peor caso de una llamada (timeout HTTP de 15 s por tres intentos ante 401, más el storage).
     */
    public static final Duration ENVIO_SIN_RESPUESTA = Duration.ofMinutes(5);

    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final SunatBillingGateway gateway;
    private final CdrParser cdrParser;
    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final Clock clock;
    private final RechazoDeSolRepository rechazosDeSol;
    /** Para la boleta que pasó el envío individual; sin él (tests que no envían boletas viejas) se cierra como fuera de plazo. */
    private final InformarEnResumenUseCase resumen;
    /** LOG-01: con un fault «ya registrado» pide a SUNAT la constancia (getStatusCdr) en vez de rechazar; sin él (tests) el comprobante queda en ERROR_ENVIO con el motivo. */
    private final RecuperarCdrUseCase recuperador;

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage, SunatBillingGateway gateway,
                                  CdrParser cdrParser, OutboxRepository outbox, UnitOfWork uow, Clock clock) {
        this(comprobantes, tenants, storage, gateway, cdrParser, outbox, uow, clock, RechazoDeSolRepository.NINGUNO, null);
    }

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage, SunatBillingGateway gateway,
                                  CdrParser cdrParser, OutboxRepository outbox, UnitOfWork uow, Clock clock, RechazoDeSolRepository rechazosDeSol) {
        this(comprobantes, tenants, storage, gateway, cdrParser, outbox, uow, clock, rechazosDeSol, null);
    }

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage, SunatBillingGateway gateway,
                                  CdrParser cdrParser, OutboxRepository outbox, UnitOfWork uow, Clock clock, RechazoDeSolRepository rechazosDeSol,
                                  InformarEnResumenUseCase resumen) {
        this(comprobantes, tenants, storage, gateway, cdrParser, outbox, uow, clock, rechazosDeSol, resumen, null);
    }

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage, SunatBillingGateway gateway,
                                  CdrParser cdrParser, OutboxRepository outbox, UnitOfWork uow, Clock clock, RechazoDeSolRepository rechazosDeSol,
                                  InformarEnResumenUseCase resumen, RecuperarCdrUseCase recuperador) {
        this.recuperador = recuperador;
        this.resumen = resumen;
        this.comprobantes = comprobantes;
        this.tenants = tenants;
        this.storage = storage;
        this.gateway = gateway;
        this.cdrParser = cdrParser;
        this.outbox = outbox;
        this.uow = uow;
        this.clock = clock;
        this.rechazosDeSol = rechazosDeSol;
    }

    @Override
    public Comprobante enviar(UUID tenantId, UUID comprobanteId) {
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        if (!c.estado().esEnviable())
            throw new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado " + c.estado());
        // Pasado el envío individual una boleta ya no va sola (1079): SUNAT la recibe en un resumen diario, en plazo hasta el séptimo día (274-H1) y
        // después fuera de plazo (#331): SUNAT puede observarla con la 4443, y e-beta la aceptó sin observaciones. Las de una misma fecha de emisión viajan juntas en un solo RC de hasta 500 líneas (ver DarDeBajaService.informar, #301).
        if (c.soloPorResumen(LocalDate.now(clock))) {
            if (resumen != null) return resumen.informar(tenantId, comprobanteId);
            throw new DomainException("FUERA_DE_PLAZO", c.nombreArchivo() + ": 1079 - Pasado el envío individual, SUNAT solo recibe " + (c.esNota() ? "esta nota" : "esta boleta") + " en un resumen diario");
        }
        // Pasado el plazo sin canal SUNAT rechaza (2108 en facturas y sus notas, 1079 en la NC 10 / ND 03 de boleta) y el número ya está consumido: se cierra
        // aquí, sin gastar el envío (#37). Una boleta nunca llega acá: va al resumen de arriba (#331). Una nota se vuelve a emitir con fecha vigente.
        if (c.fueraDePlazo(LocalDate.now(clock))) {
            c.marcarFueraDePlazo(LocalDate.now(clock));
            uow.ejecutar(() -> comprobantes.guardar(c));
            throw new DomainException("FUERA_DE_PLAZO", c.nombreArchivo() + ": " + c.ultimoError() + ". Emita un comprobante nuevo");
        }
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirCredencialesSol();
        // LOG-01: el envío se reserva antes de llamar a SUNAT, en una transacción corta y condicional (sigue FIRMADO/ERROR_ENVIO y nadie lo tiene reservado). Dos
        // envíos del mismo comprobante dentro de los 15 s del sendBill (un POST y el outbox, o el reintento del administrador) mandaban el mismo número dos veces y el
        // segundo recibía 1033 → RECHAZADO sobre el ACEPTADO del primero. El guardado del resultado libera la reserva; una sin respuesta caduca (ENVIO_SIN_RESPUESTA).
        Instant ahora = clock.instant();
        boolean reservado = uow.ejecutar(() -> comprobantes.reservarEnvio(tenantId, comprobanteId, ahora, ahora.minus(ENVIO_SIN_RESPUESTA)));
        if (!reservado)
            throw new DomainException("ENVIO_EN_CURSO", c.nombreArchivo() + ": otro proceso lo está enviando a SUNAT o acaba de resolverlo; el mismo comprobante no se manda dos veces. "
                    + "Consulte su estado en unos segundos");

        String credencialesRechazadas = null;
        try {
            byte[] xml = storage.leer(c.xmlKey());
            byte[] cdrZip = gateway.sendBill(tenant, c.nombreArchivo(), xml);
            Cdr cdr = cdrParser.parsear(cdrZip);
            String cdrKey = c.xmlKey().substring(0, c.xmlKey().lastIndexOf('/') + 1) + "R-" + c.nombreArchivo() + ".zip";
            storage.guardar(cdrKey, cdrZip);
            c.marcarEnviado();
            c.aplicarCdr(cdr, cdrKey);
        } catch (SunatCredencialesException e) {
            // #107: no es una caída de SUNAT ni un rechazo del comprobante. Queda pendiente, con un mensaje que dice qué hacer, y la empresa se marca:
            // el outbox no sigue golpeando a SUNAT con credenciales que no acepta, y las retoma solo cuando se corrigen.
            credencialesRechazadas = e.codigo() + " - " + e.getMessage();
            c.marcarErrorEnvio("SUNAT rechazó las credenciales SOL de la empresa (" + credencialesRechazadas
                    + "). Corrígelas en Fiscal & certificado: el envío se reanuda solo.");
        } catch (SunatTransientException e) {
            c.marcarErrorEnvio(e.codigo() + " - " + e.getMessage());
        } catch (SunatRechazoException e) {
            if (FaultsSunat.yaEnSunat(e.codigo())) {
                // LOG-01: «registrado previamente» no es un rechazo: un envío anterior llegó (timeout, caída antes de guardar) y SUNAT lo tiene. Se le pide la constancia;
                // si no se puede (boleta, liquidación, agente, e-beta, SUNAT caído), queda en ERROR_ENVIO con el motivo y nunca RECHAZADO sobre algo que SUNAT aceptó.
                Comprobante recuperado = recuperarDeSunat(tenant, c);
                if (recuperado != null) {
                    uow.ejecutar(() -> outbox.completarPorAgregado(c.id(), ACCION_ENVIAR));
                    return recuperado;
                }
                c.marcarErrorEnvio(e.codigo() + " - " + e.descripcion() + ". SUNAT ya tiene registrado este comprobante: un envío anterior llegó aunque khipu no recibió la respuesta. "
                        + (c.consultableEnGetStatusCdr() ? "Su constancia se recupera por getStatusCdr (en producción)" : "No se consulta por getStatusCdr: verifique su CDR en SOL y, si está aceptado, no lo reemita"));
            } else {
                c.rechazarPorFault(e.codigo(), e.descripcion());
            }
        } catch (IllegalStateException e) {   // storage, ZIP o CDR ilegible: infraestructura, reintentable
            c.marcarErrorEnvio("INFRA - " + e.getMessage());
        } catch (RuntimeException e) {
            // LOG-02: cualquier otra falla después del sendBill (red hacia S3 que el SDK no envuelve en S3Exception, un CDR que el dominio no sabe leer,
            // un Base64 roto). Sin este catch la excepción salía antes de la transacción de abajo y el comprobante quedaba FIRMADO sin fila de outbox:
            // SUNAT ya lo tenía aceptado, nadie lo reintentaba y el barrido de plazo lo cerraba FUERA_DE_PLAZO. Mismo criterio que DarDeBajaService.avanzar.
            c.marcarErrorEnvio("INFRA - " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        // El estado y la programación del reintento se confirman en la misma transacción: no puede quedar
        // un ERROR_ENVIO sin fila en el outbox ni una fila en el outbox sin el estado persistido.
        // OutboxRepository.programar es idempotente por (agregado_id, accion): cuando quien invoca es el
        // OutboxWorker, la fila ya existe (la tomó bloqueada) y este programar es un no-op; el worker
        // la reprograma o completa después, y sigue siendo la única ruta de reintento.
        String motivo = credencialesRechazadas;
        uow.ejecutar(() -> {
            comprobantes.guardar(c);
            if (motivo != null) rechazosDeSol.marcar(tenantId, motivo, clock.instant());
            // SUNAT respondió a estas credenciales: si estaban marcadas (se corrigieron por otro camino), ya no lo están.
            else if (c.estado() != EstadoDocumento.ERROR_ENVIO) rechazosDeSol.levantar(tenantId, clock.instant());
            if (c.estado() == EstadoDocumento.ERROR_ENVIO)
                outbox.programar(tenantId, ACCION_ENVIAR, c.id(), Backoff.siguiente(c.intentos(), clock.instant()));
        });
        return c;
    }

    /**
     * La constancia de un comprobante que SUNAT dice tener ya: solo la factura y sus notas (serie F) en producción responden getStatusCdr. Devuelve el comprobante
     * resuelto (ACEPTADO, ACEPTADO_CON_OBS o RECHAZADO según el CDR, ya persistido por {@link RecuperarCdrUseCase}) o {@code null} si no se pudo: el llamador lo deja
     * en ERROR_ENVIO con el motivo y el barrido de CDR lo vuelve a intentar.
     */
    private Comprobante recuperarDeSunat(Tenant tenant, Comprobante c) {
        if (recuperador == null || !c.consultableEnGetStatusCdr() || tenant.entorno() != Entorno.PRODUCCION) return null;
        try {
            Comprobante r = recuperador.recuperar(tenant.id(), c.id());
            return r.cdrKey() != null && !r.estado().esEnviable() && r.estado() != EstadoDocumento.ENVIADO ? r : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
