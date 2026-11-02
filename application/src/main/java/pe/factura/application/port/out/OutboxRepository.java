package pe.factura.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    /** Idempotente por (agregadoId, accion): si ya hay una fila pendiente para ese agregado y acción, no hace nada. */
    void programar(UUID tenantId, String accion, UUID agregadoId, Instant cuando);
    List<OutboxItem> tomarVencidas(int limite, Duration lock); // FOR UPDATE SKIP LOCKED + marca locked_until
    void reprogramar(UUID id, Instant cuando, String error);
    void completar(UUID id);
    /** Saca de la cola la tarea {@code accion} de ese agregado, si la hay (p. ej. cuando un administrador descarta un envío, #196). No hace nada si no existe. */
    void completarPorAgregado(UUID agregadoId, String accion);
}
