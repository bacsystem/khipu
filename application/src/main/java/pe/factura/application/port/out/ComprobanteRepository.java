package pe.factura.application.port.out;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.TipoDocumento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteRepository {
    void guardar(Comprobante c); // insert o update por id
    Optional<Comprobante> buscar(UUID tenantId, UUID id);
    boolean existe(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina);
    long contar(UUID tenantId, EstadoDocumento estado);
}
