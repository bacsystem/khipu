package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tokens del enlace de verificación del correo (#22). Solo se guarda su hash. */
public interface VerificacionCorreoRepository {
    record Token(String tokenHash, UUID usuarioId, Instant expiraEn, boolean usado) {}

    void crear(Token t);

    Optional<Token> buscar(String tokenHash);

    /** Lo marca usado si no lo estaba y devuelve si lo marcó: dos clics simultáneos en el mismo enlace no lo usan dos veces. */
    boolean usar(String tokenHash);

    /** Cuántos enlaces del usuario siguen sin vencer a {@code ahora}, usados o no: como todos duran lo mismo, los que se le mandaron hace menos de esa vida. */
    int contarSinVencer(UUID usuarioId, Instant ahora);
}
