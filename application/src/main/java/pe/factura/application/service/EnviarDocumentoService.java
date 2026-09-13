package pe.factura.application.service;

import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

public class EnviarDocumentoService implements EnviarDocumentoUseCase {
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;
    private final DocumentStorage storage;
    private final SunatBillingGateway gateway;
    private final CdrParser cdrParser;
    private final UnitOfWork uow;

    public EnviarDocumentoService(ComprobanteRepository comprobantes, TenantRepository tenants, DocumentStorage storage,
                                  SunatBillingGateway gateway, CdrParser cdrParser, UnitOfWork uow) {
        this.comprobantes = comprobantes; this.tenants = tenants; this.storage = storage;
        this.gateway = gateway; this.cdrParser = cdrParser; this.uow = uow;
    }

    @Override
    public Comprobante enviar(UUID tenantId, UUID comprobanteId) {
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
        if (!c.estado().esEnviable())
            throw new DomainException("ESTADO_NO_ENVIABLE", "El comprobante está en estado " + c.estado());
        Tenant tenant = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));
        tenant.exigirCredencialesSol();

        byte[] xml = storage.leer(c.xmlKey());
        try {
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
        }
        uow.ejecutar(() -> comprobantes.guardar(c));
        return c;
    }
}
