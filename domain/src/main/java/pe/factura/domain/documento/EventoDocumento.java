package pe.factura.domain.documento;

import java.time.Instant;

/**
 * Un cambio de estado del comprobante con lo que lo motivó (#4): el motivo del error de envío, el código y descripción del
 * CDR, la baja aceptada… Se acumulan en el comprobante al transitar y el repositorio los persiste con la fecha del servidor
 * ({@code ocurridoEn} es {@code null} mientras está pendiente de guardar).
 */
public record EventoDocumento(EstadoDocumento estadoAnterior, EstadoDocumento estadoNuevo, String detalle, Instant ocurridoEn) {
    public static EventoDocumento pendiente(EstadoDocumento anterior, EstadoDocumento nuevo, String detalle) {
        return new EventoDocumento(anterior, nuevo, detalle, null);
    }
}
