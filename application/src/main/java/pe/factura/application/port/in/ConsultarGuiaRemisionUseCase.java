package pe.factura.application.port.in;

import pe.factura.application.port.out.GuiaRemisionRepository.Filtro;
import pe.factura.domain.documento.GuiaRemision;

import java.util.List;
import java.util.UUID;

/** Consulta de las guías de remisión de una empresa (#39) y de sus archivos: XML firmado, CDR y representación impresa. */
public interface ConsultarGuiaRemisionUseCase {
    GuiaRemision obtener(UUID tenantId, UUID id);
    List<GuiaRemision> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, Filtro filtro);
    byte[] xml(UUID tenantId, UUID id);
    /** El ZIP del CDR tal como lo devolvió SUNAT; {@code DomainException("SIN_CDR")} si todavía no hay. */
    byte[] cdr(UUID tenantId, UUID id);
    /** El ApplicationResponse del CDR. */
    byte[] cdrXml(UUID tenantId, UUID id);
    byte[] pdf(UUID tenantId, UUID id);
}
