package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Claves de idempotencia (#115): que un reintento de la misma operación devuelva lo que ya se hizo en vez de hacerlo otra vez.
 * {@code alcance} separa las claves de cada tenant y de cada operación; la misma clave en dos alcances son dos claves.
 */
public interface IdempotenciaRepository {
    /**
     * Lo que quedó registrado para una clave: la huella del pedido, el recurso que produjo y, si la operación lo guardó, su respuesta
     * cifrada (#219: la del alta asistida lleva una API key que no se puede reconstruir). {@code respuestaCifrada} es nula si no se
     * guardó o si ya se olvidó. {@code creadoAt}: cuándo se reservó la clave; con él quien lee hace cumplir la vida de la respuesta sin
     * esperar a la limpieza, que corre cada hora.
     */
    record Registro(String huella, UUID recursoId, byte[] respuestaCifrada, Instant creadoAt) {
        public Registro(String huella, UUID recursoId) { this(huella, recursoId, null, null); }
    }

    /**
     * Lo que ya está registrado para la clave, sin reservar nada ni esperar a nadie: solo ve lo confirmado. Sirve para contestar un
     * reintento antes de volver a validar un pedido que ya se hizo; la exclusión entre pedidos simultáneos sigue siendo de {@link #reservar}.
     */
    Optional<Registro> buscar(String alcance, String clave);

    /**
     * Reserva la clave para esta operación y devuelve vacío; si ya existía, devuelve lo registrado y no reserva nada. Debe llamarse
     * dentro de la transacción de la operación: si esta se revierte, la reserva también, y el reintento puede volver a hacerla. Si
     * otra transacción tiene la misma clave reservada y sin confirmar, espera a que termine: dos pedidos simultáneos no pueden
     * hacer los dos la operación.
     */
    Optional<Registro> reservar(String alcance, String clave, String huella);

    /** Anota el recurso que produjo la operación de una clave reservada; en la misma transacción que la reserva. */
    default void completar(String alcance, String clave, UUID recursoId) { completar(alcance, clave, recursoId, null); }

    /** Ídem, con la respuesta de la operación ya cifrada, para devolverla igual en un reintento. */
    void completar(String alcance, String clave, UUID recursoId, byte[] respuestaCifrada);

    /** Borra las claves reservadas antes de {@code limite} y devuelve cuántas: la tabla no crece sin límite. */
    int borrarAnterioresA(Instant limite);

    /**
     * Borra la respuesta guardada de las claves reservadas antes de {@code limite}, pero conserva la clave: un reintento tardío se
     * reconoce, aunque ya no se le pueda devolver lo que contenía. Devuelve cuántas respuestas olvidó.
     */
    int olvidarRespuestasAnterioresA(Instant limite);
}
