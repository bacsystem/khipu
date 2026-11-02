package pe.factura.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Estado del segundo factor de cada administrador (#177). El secreto se guarda cifrado; los códigos de recuperación, solo su hash. */
public interface SegundoFactorRepository {
    /**
     * @param ultimoPaso el último paso TOTP aceptado: un código de ese paso o de uno anterior ya no vale.
     * @param bloqueadoHasta tras demasiados códigos fallidos, hasta cuándo no se acepta ninguno; {@code null} si no está bloqueado.
     */
    record Estado(byte[] secretoCifrado, boolean confirmado, long ultimoPaso, int fallos, Instant bloqueadoHasta) {}

    Optional<Estado> buscar(UUID administradorId);

    /** Un secreto nuevo, sin confirmar: reemplaza el anterior y borra sus códigos de recuperación. */
    void guardarPendiente(UUID administradorId, byte[] secretoCifrado);

    /** Confirma el secreto con el primer código aceptado y guarda los hashes de los códigos de recuperación. */
    void confirmar(UUID administradorId, long paso, List<String> hashesRecuperacion);

    /**
     * Registra un código aceptado, pone los fallos en cero y levanta el bloqueo que dejó su propio intento (ver
     * {@link #reservarIntento}). Devuelve {@code false} si ya se aceptó uno de ese paso o posterior: es la barrera contra reusar un
     * código, también entre dos peticiones simultáneas.
     */
    boolean registrarAcceso(UUID administradorId, long paso);

    /**
     * Reserva un intento de código <b>antes</b> de comprobarlo. Devuelve {@code false}, sin sumar nada, si la cuenta está bloqueada
     * en {@code ahora}; si no, suma el intento y, si con él se llega a {@code maxIntentos}, deja la cuenta bloqueada hasta
     * {@code bloquearHasta} y la cuenta de intentos en cero. El quinto intento todavía se concede: ya quedó reservado.
     * <p>
     * Es una sola sentencia atómica y no una lectura seguida de una escritura: con la compuerta separada del conteo, N peticiones
     * simultáneas que leen «sin bloqueo» antes de que ninguna cuente su fallo prueban las N un código. Un acierto reinicia la cuenta
     * ({@link #registrarAcceso}, {@link #consumirCodigoRecuperacion}); un fallo no necesita escribir nada más, porque ya se contó.
     * Quien llama lo invoca <b>fuera</b> de la transacción de la petición: dentro, se revertiría con el error y el bloqueo nunca llegaría.
     */
    boolean reservarIntento(UUID administradorId, int maxIntentos, Instant ahora, Instant bloquearHasta);

    /**
     * Marca usado el código de recuperación con ese hash, pone los fallos en cero y levanta el bloqueo de su propio intento.
     * {@code false} si no existe o ya se usó: cada uno sirve una sola vez.
     */
    boolean consumirCodigoRecuperacion(UUID administradorId, String hash);
}
