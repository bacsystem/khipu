package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.ComunicacionBaja.EstadoBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comunicación de baja (#31): un VoidedDocuments por comprobante, enviado con {@code sendSummary}. SUNAT responde un ticket;
 * el CDR se recoge con {@code getStatus} — en el acto si ya está procesado, o desde el outbox (acción {@link #ACCION_BAJA})
 * mientras SUNAT devuelva 98. Al aceptarse, el comprobante pasa a {@code ANULADO} en la misma transacción.
 */
@RequiredArgsConstructor
public class DarDeBajaService implements DarDeBajaUseCase {
    public static final String ACCION_BAJA = "BAJA";
    /** SUNAT suele procesar un RA en segundos: se reconsulta pronto, sin el backoff largo de los envíos fallidos. */
    public static final Duration REINTENTO_CONSULTA = Duration.ofSeconds(30);

    private final BajaRepository bajas;
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final UblGenerator ubl;
    private final XsdValidator xsd;
    private final XmlSigner signer;
    private final SunatBillingGateway gateway;
    private final CdrParser cdrParser;
    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override
    public ComunicacionBaja solicitar(UUID tenantId, UUID comprobanteId, String motivo) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirListoParaEmitir(LocalDate.now(clock));
        tenant.exigirCredencialesSol();
        ComunicacionBaja baja = uow.ejecutar(() -> {
            Comprobante c = comprobantes.bloquear(tenantId, comprobanteId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
            if (bajas.deComprobante(tenantId, c.id()).stream().anyMatch(ComunicacionBaja::pendiente))
                throw new DomainException("BAJA_INVALIDA", "Ya hay una comunicación de baja en curso para " + c.serie() + "-" + c.numero());
            ComunicacionBaja b = ComunicacionBaja.crear(c, bajas.siguienteCorrelativo(tenantId, LocalDate.now(clock)), motivo, clock);
            String xml = ubl.generarBaja(b, tenant);
            FirmaResultado firma = signer.firmar(xml, tenant.certificado());
            xsd.validarBaja(firma.xmlFirmado());
            String key = tenantId + "/" + b.fechaGeneracion().getYear() + "/" + String.format("%02d", b.fechaGeneracion().getMonthValue()) + "/" + b.nombreArchivo(tenant.ruc()) + ".xml";
            storage.guardar(key, firma.xmlFirmado().getBytes(StandardCharsets.UTF_8));
            b.firmar(key);
            bajas.guardar(b);
            return b;
        });
        return continuar(tenant, baja);
    }

    @Override
    public ComunicacionBaja continuar(UUID tenantId, UUID bajaId) {
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        return continuar(tenant, obtener(tenantId, bajaId));
    }

    private ComunicacionBaja continuar(Tenant tenant, ComunicacionBaja b) {
        if (!b.pendiente()) return b;
        try {
            if (b.estado() != EstadoBaja.ENVIADA) {
                String ticket = gateway.sendSummary(tenant, b.nombreArchivo(tenant.ruc()), storage.leer(b.xmlKey()));
                b.marcarEnviada(ticket);
            }
            SunatBillingGateway.EstadoTicket estado = gateway.getStatus(tenant, b.ticket());
            if (estado.procesado()) {
                Cdr cdr = cdrParser.parsear(estado.cdrZip());
                String cdrKey = b.xmlKey().substring(0, b.xmlKey().lastIndexOf('/') + 1) + "R-" + b.nombreArchivo(tenant.ruc()) + ".zip";
                storage.guardar(cdrKey, estado.cdrZip());
                b.aplicarCdr(cdr, cdrKey);
            } else {
                b.registrarConsultaPendiente("98 - SUNAT sigue procesando el ticket " + b.ticket());
            }
        } catch (SunatTransientException e) {
            if (b.estado() == EstadoBaja.ENVIADA) b.registrarConsultaPendiente(e.codigo() + " - " + e.getMessage());
            else b.marcarErrorEnvio(e.codigo() + " - " + e.getMessage());
        } catch (SunatRechazoException e) {
            b.rechazarPorFault(e.codigo(), e.descripcion());
        } catch (IllegalStateException e) {   // storage, ZIP o CDR ilegible: infraestructura, reintentable
            if (b.estado() == EstadoBaja.ENVIADA) b.registrarConsultaPendiente("INFRA - " + e.getMessage()); else b.marcarErrorEnvio("INFRA - " + e.getMessage());
        }
        // Estado de la baja, anulación del comprobante y reprogramación en una sola transacción (mismo criterio que EnviarDocumentoService).
        uow.ejecutar(() -> {
            bajas.guardar(b);
            if (b.estado() == EstadoBaja.ACEPTADA) {
                // Con lock de fila: el GET del usuario y el outbox pueden recoger el mismo CDR a la vez, y el segundo se encuentra el comprobante ya ANULADO.
                Comprobante c = comprobantes.bloquear(b.tenantId(), b.comprobanteId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
                if (c.estado() != EstadoDocumento.ANULADO) {
                    c.anular();
                    comprobantes.guardar(c);
                }
            }
            if (b.estado() == EstadoBaja.ENVIADA) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), clock.instant().plus(REINTENTO_CONSULTA));
            if (b.estado() == EstadoBaja.ERROR_ENVIO) outbox.programar(b.tenantId(), ACCION_BAJA, b.id(), Backoff.siguiente(b.intentos(), clock.instant()));
        });
        return b;
    }

    @Override public ComunicacionBaja obtener(UUID tenantId, UUID bajaId) {
        return bajas.buscar(tenantId, bajaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comunicación de baja no encontrada"));
    }
    @Override public List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId) { return bajas.deComprobante(tenantId, comprobanteId); }
}
