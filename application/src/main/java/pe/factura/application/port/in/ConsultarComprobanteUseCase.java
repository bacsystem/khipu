package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.util.List;
import java.util.UUID;

public interface ConsultarComprobanteUseCase {
    Comprobante obtener(UUID tenantId, UUID id);                       // DomainException("NO_ENCONTRADO")
    List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina);
    long contar(UUID tenantId, EstadoDocumento estado);
    byte[] xml(UUID tenantId, UUID id);                                // bytes del XML firmado
    byte[] cdr(UUID tenantId, UUID id);                                // ZIP del CDR; DomainException("SIN_CDR")
    byte[] cdrXml(UUID tenantId, UUID id);                             // XML dentro del ZIP del CDR
}
