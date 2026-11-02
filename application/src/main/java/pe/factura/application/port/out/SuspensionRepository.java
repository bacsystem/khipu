package pe.factura.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Si una cuenta está suspendida (#182). Una cuenta suspendida no entra al portal y ninguna de sus empresas emite por API; no se borra nada,
 * así que reactivarla lo devuelve todo a como estaba. Las empresas sin cuenta (las de integración) nunca están suspendidas.
 */
public interface SuspensionRepository {
    boolean cuentaSuspendida(UUID cuentaId);

    /** Si la cuenta dueña de la empresa está suspendida; {@code false} si la empresa no existe o no tiene cuenta. */
    boolean empresaSuspendida(UUID tenantId);

    /** Suspende la cuenta. {@code true} si estaba activa; {@code false} si ya estaba suspendida (no cambia nada). Atómico: de dos pedidos a la vez, uno solo lo logra. */
    boolean suspender(UUID cuentaId, Instant cuando);

    /** Reactiva la cuenta. {@code true} si estaba suspendida; {@code false} si ya estaba activa. Atómico, igual que {@link #suspender}. */
    boolean reactivar(UUID cuentaId);
}
