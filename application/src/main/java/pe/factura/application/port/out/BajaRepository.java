package pe.factura.application.port.out;

import pe.factura.domain.documento.ComunicacionBaja;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BajaRepository {
    void guardar(ComunicacionBaja b);   // insert o update por id
    Optional<ComunicacionBaja> buscar(UUID tenantId, UUID id);
    /** Comunicaciones de baja de un comprobante, de la más reciente a la más antigua. */
    List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId);
    /** Siguiente correlativo RA-yyyymmdd-N de la empresa para ese día; debe llamarse dentro de la transacción que guarda la baja. */
    int siguienteCorrelativo(UUID tenantId, LocalDate fecha);
}
