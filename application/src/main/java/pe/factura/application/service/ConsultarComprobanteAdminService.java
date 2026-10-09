package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultarComprobanteAdminService implements ConsultarComprobanteAdminUseCase {
    // Ubicar un comprobante sin saber su empresa es lo mismo que necesitan las acciones de la cola de errores (#196).
    private final ColaDeErroresRepository cola;
    private final ComprobanteRepository comprobantes;
    private final TenantRepository tenants;

    @Override public Ficha ficha(UUID comprobanteId) {
        UUID tenantId = cola.ubicar(comprobanteId).map(ColaDeErroresRepository.Ubicacion::tenantId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Tenant t = tenants.buscar(tenantId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Cdr cdr = c.cdr();
        return new Ficha(c.id(), tenantId, t.ruc(), t.razonSocial(), tenants.cuentaDe(tenantId).orElse(null), c.nombreArchivo(), c.tipo().codigo(), c.serie(), c.numero(),
                c.fechaEmision(), c.estado(), c.intentos(), c.ultimoError(), cdr == null ? null : new RespuestaSunat(cdr.codigo(), cdr.descripcion()),
                c.xmlKey() != null, c.cdrKey() != null);
    }

    private static DomainException noEncontrado() {
        return new DomainException("NO_ENCONTRADO", "Comprobante no encontrado");
    }
}
