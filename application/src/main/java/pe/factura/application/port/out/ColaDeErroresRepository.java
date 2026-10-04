package pe.factura.application.port.out;

import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que lee la cola global de errores (#196): los comprobantes con problema de todas las empresas, con su empresa y su cuenta. Solo datos crudos; la clase de error y el fault
 * los deciden {@code ClaseDeError} y {@code FaultSunat}.
 */
public interface ColaDeErroresRepository {
    /** Una fila de la cola. {@code siguienteIntento}: el del outbox, nulo si no hay reintento programado. {@code cuentaId} y {@code cuentaNombre} son nulos en una empresa de integración. */
    record Fila(UUID comprobanteId, UUID tenantId, String ruc, String razonSocial, UUID cuentaId, String cuentaNombre, String nombreArchivo, String tipo, String serie, long numero,
                LocalDate fechaEmision, EstadoDocumento estado, int intentos, String ultimoError, String cdrCodigo, String cdrDescripcion, Instant siguienteIntento,
                Instant actualizadoEn) {}

    /** De qué empresa es un comprobante, para actuar sobre él sin que el administrador tenga que saberlo. */
    record Ubicacion(UUID tenantId, String nombreArchivo) {}

    /** De la más urgente (fecha de emisión más antigua) a la menos, paginada desde 1. */
    List<Fila> listar(Filtro filtro, int pagina, int porPagina);

    /** Cuántos hay con ese filtro, sin paginar. */
    long contar(Filtro filtro);

    /** Vacío si el comprobante no existe. */
    Optional<Ubicacion> ubicar(UUID comprobanteId);
}
