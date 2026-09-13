package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ConsultarComprobanteService implements ConsultarComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final DocumentStorage storage;

    public Comprobante obtener(UUID tenantId, UUID id) {
        return comprobantes.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }
    public List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina) { return comprobantes.listar(tenantId, estado, pagina, porPagina); }
    public byte[] xml(UUID tenantId, UUID id) { return storage.leer(obtener(tenantId, id).xmlKey()); }
    public byte[] cdr(UUID tenantId, UUID id) {
        Comprobante c = obtener(tenantId, id);
        if (c.cdrKey() == null) throw new DomainException("SIN_CDR", "El comprobante aún no tiene CDR");
        return storage.leer(c.cdrKey());
    }
}
