package pe.factura.application.service;

import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.InformarEnResumenUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public class EnviarDocumentoService implements EnviarDocumentoUseCase {
    public static final String ACCION_ENVIAR = "ENVIAR";

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
        // Pasado el envío individual una boleta ya no va sola (1079): SUNAT la recibe en un resumen diario hasta el séptimo día (274-H1).
        if (c.soloPorResumen(LocalDate.now(clock))) {
            if (resumen != null) return resumen.informar(tenantId, comprobanteId);
            throw new DomainException("FUERA_DE_PLAZO", c.nombreArchivo() + ": 1079 - Pasado el envío individual, SUNAT solo recibe esta boleta en un resumen diario");
        }
        // Pasado el plazo SUNAT rechaza (2108, o 1079 en boletas) y el número ya está consumido: se cierra aquí, sin gastar el envío (#37).
        if (c.fueraDePlazo(LocalDate.now(clock))) {
            c.marcarFueraDePlazo(LocalDate.now(clock));
            uow.ejecutar(() -> comprobantes.guardar(c));
            // «Emita uno nuevo» solo para la factura: una boleta no se reemplaza, SUNAT la sigue recibiendo en un resumen diario (274-H1).
            throw new DomainException("FUERA_DE_PLAZO", c.nombreArchivo() + ": " + c.ultimoError() + (c.tipo() == TipoDocumento.BOLETA ? "" : ". Emita un comprobante nuevo"));
        }
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirCredencialesSol();

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
            c.rechazarPorFault(e.codigo(), e.descripcion());
        } catch (IllegalStateException e) {   // storage, ZIP o CDR ilegible: infraestructura, reintentable
            c.marcarErrorEnvio("INFRA - " + e.getMessage());
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
}
