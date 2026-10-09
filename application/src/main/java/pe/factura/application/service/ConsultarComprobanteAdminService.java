package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
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
    private final DocumentStorage storage;

    @Override public Ficha ficha(UUID comprobanteId) {
        UUID tenantId = cola.ubicar(comprobanteId).map(ColaDeErroresRepository.Ubicacion::tenantId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Comprobante c = comprobantes.buscar(tenantId, comprobanteId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Tenant t = tenants.buscar(tenantId).orElseThrow(ConsultarComprobanteAdminService::noEncontrado);
        Cdr cdr = c.cdr();
        return new Ficha(c.id(), tenantId, t.ruc(), t.razonSocial(), tenants.cuentaDe(tenantId).orElse(null), c.nombreArchivo(), c.tipo().codigo(), c.serie(), c.numero(),
                c.fechaEmision(), c.estado(), c.intentos(), c.ultimoError(), cdr == null ? null : new RespuestaSunat(cdr.codigo(), cdr.descripcion()),
                guardado(c.xmlKey()), guardado(c.cdrKey()));
    }

    /**
     * Que el objeto esté en el almacenamiento, no solo su clave en la base (273-H1): la verificación de integridad marca XML_FALTANTE y CDR_FALTANTE justo
     * cuando la clave está y el objeto no, y desde ese fallo se llega a esta ficha.
     */
    private boolean guardado(String key) {
        return key != null && storage.existe(key);
    }

    private static DomainException noEncontrado() {
        return new DomainException("NO_ENCONTRADO", "Comprobante no encontrado");
    }
}
