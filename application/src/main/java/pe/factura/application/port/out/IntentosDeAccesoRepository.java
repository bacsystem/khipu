package pe.factura.application.port.out;

import java.time.Duration;
import java.time.Instant;

/**
 * Contadores de intentos con ventana de tiempo (#261): fallos de login por correo y por IP, y correos de recuperación por
 * dirección. Cada clave es un texto opaco que arma quien llama ({@code LimiteDeIntentos}); el repositorio no sabe qué cuenta.
 */
public interface IntentosDeAccesoRepository {
    /**
     * Reserva un intento para {@code clave} <b>antes</b> de hacer lo que se limita. Devuelve {@code false}, sin sumar nada, si la
     * clave está bloqueada en {@code ahora}. Si no, suma el intento (empezando de cero si la ventana anterior ya venció) y, si con él
     * se llega a {@code maxIntentos}, deja la clave bloqueada {@code bloqueo} desde {@code ahora}. El intento que llega al tope
     * todavía se concede: ya quedó reservado.
     * <p>
     * Una sola sentencia atómica y no una lectura seguida de una escritura: con la compuerta separada del conteo, N peticiones
     * simultáneas que leen «sin bloqueo» antes de que ninguna cuente prueban las N. Quien llama lo invoca <b>fuera</b> de la
     * transacción de la petición: dentro, se revertiría con el error y el bloqueo nunca llegaría.
     */
    boolean reservar(String clave, int maxIntentos, Instant ahora, Duration ventana, Duration bloqueo);

    /**
     * Devuelve un intento ya reservado que no era un fallo (un login correcto): resta uno y levanta el bloqueo que pudo dejar ese
     * mismo intento. Sirve para el contador por IP, donde un acierto de un usuario no debe gastar el cupo de los demás de su red.
     */
    void devolver(String clave);

    /** Olvida la clave: un login correcto demuestra que la contraseña era suya y pone en cero los fallos de su correo. */
    void reiniciar(String clave);

    /** Borra los contadores sin actividad ni bloqueo desde antes de {@code antesDe}; devuelve cuántos. */
    int purgar(Instant antesDe);
}
