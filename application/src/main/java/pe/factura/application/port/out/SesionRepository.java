package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sesiones de refresh y tokens de recuperación (se almacenan solo hashes). */
public interface SesionRepository {
    /**
     * {@code rotadaEn}/{@code reemplazadaPor}: si la sesión se cerró por rotación (un refresh), cuándo y por cuál. Revocarla por otro motivo
     * (logout, restablecer la contraseña) los deja en {@code null}: solo la rotación da la gracia de S6.
     */
    record Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada, Instant rotadaEn, UUID reemplazadaPor) {
        public Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada) {
            this(id, usuarioId, refreshHash, expiraEn, revocada, null, null);
        }
    }
    void crear(Sesion s);
    Optional<Sesion> buscarPorRefreshHash(String hash);
    Optional<Sesion> buscar(UUID id);
    /** Cierra la sesión sin gracia: borra la marca de rotación si la tenía. */
    void revocar(UUID id);
    /** Como {@link #revocar} para todas las sesiones del usuario. */
    void revocarTodas(UUID usuarioId);
    /** Cierra la sesión porque un refresh la reemplazó por {@code nueva}. Si ya estaba rotada, conserva la primera rotación. */
    void rotar(UUID id, UUID nueva, Instant en);

    record TokenRecuperacion(String tokenHash, UUID usuarioId, Instant expiraEn, boolean usado) {}
    void crearRecuperacion(TokenRecuperacion t);
    Optional<TokenRecuperacion> buscarRecuperacion(String tokenHash);
    void marcarRecuperacionUsada(String tokenHash);
}
