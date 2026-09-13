package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
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


    @Override
    public Comprobante enviar(UUID tenantId, UUID comprobanteId) {
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        if (!c.estado().esEnviable())
            throw new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado " + c.estado());
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirCredencialesSol();

        try {
            byte[] xml = storage.leer(c.xmlKey());
            byte[] cdrZip = gateway.sendBill(tenant, c.nombreArchivo(), xml);
            Cdr cdr = cdrParser.parsear(cdrZip);
            String cdrKey = c.xmlKey().substring(0, c.xmlKey().lastIndexOf('/') + 1) + "R-" + c.nombreArchivo() + ".zip";
            storage.guardar(cdrKey, cdrZip);
            c.marcarEnviado();
            c.aplicarCdr(cdr, cdrKey);
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
        uow.ejecutar(() -> {
            comprobantes.guardar(c);
            if (c.estado() == EstadoDocumento.ERROR_ENVIO)
                outbox.programar(tenantId, ACCION_ENVIAR, c.id(), Backoff.siguiente(c.intentos(), clock.instant()));
        });
        return c;
    }
}
