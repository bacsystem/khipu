package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.util.List;
import java.util.UUID;

public interface SerieRepository {
    long siguienteNumero(UUID tenantId, TipoDocumento tipo, String serie); // bloqueo de fila; DomainException("SERIE_NO_CONFIGURADA")
    /** Eleva ultimo_numero hasta {@code numero} si es mayor (nunca lo reduce); bloqueo de fila; DomainException("SERIE_NO_CONFIGURADA"). */
    void avanzarHasta(UUID tenantId, TipoDocumento tipo, String serie, long numero);
    void crear(Serie s);
    List<Serie> listar(UUID tenantId);
}
