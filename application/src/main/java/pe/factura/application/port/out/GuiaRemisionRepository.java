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
    /**
     * Reserva el envío (H2): pasa la guía a ENVIANDO con una actualización condicional, solo si está FIRMADO o ERROR_ENVIO —o, con {@code forzar}, si está
     * ENVIANDO desde antes de {@code viejaAntesDe}—. {@code false} si otro la reservó o ya no se envía: dos envíos no llaman a SUNAT con el mismo número.
     * Debe correr en una transacción corta propia, confirmada antes de llamar a SUNAT.
     */
    boolean reservarEnvio(UUID tenantId, UUID id, java.time.Instant ahora, boolean forzar, java.time.Instant viejaAntesDe);
    List<GuiaRemision> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina);
    long contar(UUID tenantId, Filtro filtro);

    /** Filtros del listado; {@code null} es «cualquiera». */
    record Filtro(EstadoGuia estado, TipoDocumento tipo, String serie, LocalDate desde, LocalDate hasta) {
        public static final Filtro NINGUNO = new Filtro(null, null, null, null, null);
    }
}
