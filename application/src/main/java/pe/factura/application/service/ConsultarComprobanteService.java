package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RequiredArgsConstructor
public class ConsultarComprobanteService implements ConsultarComprobanteUseCase {
    private final ComprobanteRepository comprobantes;
    private final DocumentStorage storage;

    public Comprobante obtener(UUID tenantId, UUID id) {
        return comprobantes.buscar(tenantId, id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Comprobante no encontrado"));
    }
    public List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina) { return comprobantes.listar(tenantId, estado, pagina, porPagina); }
    public long contar(UUID tenantId, EstadoDocumento estado) { return comprobantes.contar(tenantId, estado); }
    public byte[] xml(UUID tenantId, UUID id) { return storage.leer(obtener(tenantId, id).xmlKey()); }
    public byte[] cdr(UUID tenantId, UUID id) {
        Comprobante c = obtener(tenantId, id);
        if (c.cdrKey() == null) throw new DomainException("SIN_CDR", "El comprobante aún no tiene CDR");
        return storage.leer(c.cdrKey());
    }
    public byte[] cdrXml(UUID tenantId, UUID id) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(cdr(tenantId, id)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith(".xml")) return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new DomainException("CDR_CORRUPTO", "No se pudo leer el ZIP del CDR", e);
        }
        throw new DomainException("CDR_CORRUPTO", "El ZIP del CDR no contiene un XML");
    }
}
