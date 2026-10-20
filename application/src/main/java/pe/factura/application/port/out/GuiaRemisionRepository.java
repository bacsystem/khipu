package pe.factura.application.port.out;

import pe.factura.domain.documento.EstadoGuia;
import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.documento.TipoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Guías de remisión (#39), siempre dentro de su empresa: una guía de otra empresa no existe. */
public interface GuiaRemisionRepository {
    /**
     * Inserta la guía la primera vez y después solo actualiza su estado y lo del envío (los datos de una guía firmada no cambian). Un envío o una consulta
     * del ticket que llega tarde no pisa una guía que otra transacción ya resolvió (ACEPTADO, ACEPTADO_CON_OBS o RECHAZADO): {@code DomainException("ESTADO_CONFLICTO")}.
     */
    void guardar(GuiaRemision g);
    Optional<GuiaRemision> buscar(UUID tenantId, UUID id);
    /** La guía con su fila bloqueada hasta el fin de la transacción: el envío y la consulta del ticket no corren a la vez sobre la misma guía. */
    Optional<GuiaRemision> bloquear(UUID tenantId, UUID id);
    List<GuiaRemision> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, Filtro filtro);

    /** Filtros del listado; {@code null} es «cualquiera». */
    record Filtro(EstadoGuia estado, TipoDocumento tipo, String serie, LocalDate desde, LocalDate hasta) {
        public static final Filtro NINGUNO = new Filtro(null, null, null, null, null);
    }
}
