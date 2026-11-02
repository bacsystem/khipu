package pe.factura.application.port.in;

import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;
import pe.factura.domain.plataforma.ActorAdmin;

import java.util.UUID;

/**
 * Lo que un administrador puede hacer con un comprobante de la cola de errores (#196): reintentar su envío o dejar de intentarlo. Las dos quedan en la bitácora a nombre de
 * {@code actor}. Ninguna fuerza lo que el dominio no permite: las reglas de estado son las de siempre.
 */
public interface ResolverErroresUseCase {
    /** Cómo terminó el reintento: el estado en que quedó el comprobante, cuántos intentos lleva y el fault si volvió a fallar. */
    record Reintento(UUID comprobanteId, EstadoDocumento estado, int intentos, FaultSunat fault) {}

    record Descarte(UUID comprobanteId, EstadoDocumento estado) {}

    /** Tope del motivo de un descarte. */
    int MAX_MOTIVO = 200;

    /**
     * Reintenta el envío ahora, con las mismas reglas que el envío manual de la empresa. {@code NO_ENCONTRADO} si no existe; {@code ESTADO_NO_ENVIABLE} si ya no está por enviar;
     * {@code FUERA_DE_PLAZO} si se pasó el plazo (queda en ese estado). Que SUNAT vuelva a fallar **no** es un error: el comprobante queda en error de envío y así se informa.
     * Se anota en la bitácora aunque el reintento no se pueda hacer.
     */
    Reintento reintentar(ActorAdmin actor, UUID comprobanteId);

    /**
     * Deja de intentar un envío que falla: pasa a {@code DESCARTADO} (terminal) y se saca del outbox. Solo desde error de envío ({@code ESTADO_NO_DESCARTABLE} si no; y si
     * cambió de estado en el medio, {@code ESTADO_CONFLICTO}). {@code NO_ENCONTRADO} si no existe. El motivo es obligatorio ({@code MOTIVO_REQUERIDO}) y de hasta
     * {@link #MAX_MOTIVO} caracteres ({@code MOTIVO_LARGO}).
     */
    Descarte descartar(ActorAdmin actor, UUID comprobanteId, String motivo);
}
