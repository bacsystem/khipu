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
     * Registra un código aceptado y pone los fallos en cero. Devuelve {@code false} si ya se aceptó uno de ese paso o posterior:
     * es la barrera contra reusar un código, también entre dos peticiones simultáneas.
     */
    boolean registrarAcceso(UUID administradorId, long paso);

    /**
     * Suma un código fallido y, si con él se llega a {@code maxFallos}, bloquea hasta {@code bloquearHasta} y pone la cuenta en cero.
     * En una sola operación atómica: leer y escribir por separado dejaría que intentos en paralelo se pisen la cuenta y nunca bloqueen.
     */
    void registrarFallo(UUID administradorId, int maxFallos, Instant bloquearHasta);

    /**
     * Marca usado el código de recuperación con ese hash y pone los fallos en cero. {@code false} si no existe o ya se usó: cada uno
     * sirve una sola vez.
     */
    boolean consumirCodigoRecuperacion(UUID administradorId, String hash);
}
