package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Claves de idempotencia (#115): que un reintento de la misma operación devuelva lo que ya se hizo en vez de hacerlo otra vez.
 * {@code alcance} separa las claves de cada tenant y de cada operación; la misma clave en dos alcances son dos claves.
 */
public interface IdempotenciaRepository {
    /** Lo que quedó registrado para una clave: la huella del pedido y el recurso que produjo. */
    record Registro(String huella, UUID recursoId) {}

    /**
     * Reserva la clave para esta operación y devuelve vacío; si ya existía, devuelve lo registrado y no reserva nada. Debe llamarse
     * dentro de la transacción de la operación: si esta se revierte, la reserva también, y el reintento puede volver a hacerla. Si
     * otra transacción tiene la misma clave reservada y sin confirmar, espera a que termine: dos pedidos simultáneos no pueden
     * hacer los dos la operación.
     */
    Optional<Registro> reservar(String alcance, String clave, String huella);

    /** Anota el recurso que produjo la operación de una clave reservada; en la misma transacción que la reserva. */
    void completar(String alcance, String clave, UUID recursoId);

    /** Borra las claves reservadas antes de {@code limite} y devuelve cuántas: la tabla no crece sin límite. */
    int borrarAnterioresA(Instant limite);
}
