package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sesiones de refresh y tokens de recuperación (se almacenan solo hashes). */
public interface SesionRepository {
    /**
     * {@code rotadaEn}/{@code reemplazadaPor}: si la sesión se cerró por rotación (un refresh), cuándo y por cuál. Revocarla por otro motivo
     * (logout, restablecer la contraseña) los deja en {@code null}: solo la rotación da la gracia de S6.
     * <p>
     * {@code familia}: la del login del que sale (271-H1). Un refresh hereda la familia de la sesión que rota, también el que entra en gracia, así que
     * todo lo que salió de un mismo login en un navegador —la cadena de rotaciones y las ramas que abre la gracia— es una familia, y el logout la cierra
     * entera. Un login nuevo (otro dispositivo) es otra familia.
     */
    record Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada, Instant rotadaEn, UUID reemplazadaPor, UUID familia) {
        /** Una sesión nueva de un login: su propia familia. */
        public Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada) {
            this(id, usuarioId, refreshHash, expiraEn, revocada, null, null, id);
        }
        /** Una sesión nueva que sale de un refresh: la familia de la que rota. */
        public Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, UUID familia) {
            this(id, usuarioId, refreshHash, expiraEn, false, null, null, familia);
        }
    }
    void crear(Sesion s);
    Optional<Sesion> buscarPorRefreshHash(String hash);
    Optional<Sesion> buscar(UUID id);
    /** Cierra la sesión sin gracia: borra la marca de rotación si la tenía. */
    void revocar(UUID id);
    /** Como {@link #revocar} para toda la familia (el logout de un navegador): ninguna de sus sesiones ni de sus refresh rotados sirve después. */
    void revocarFamilia(UUID familia);
    /** Como {@link #revocar} para todas las sesiones del usuario. */
    void revocarTodas(UUID usuarioId);
    /** Cierra la sesión porque un refresh la reemplazó por {@code nueva}. Si ya estaba rotada, conserva la primera rotación. */
    void rotar(UUID id, UUID nueva, Instant en);

    record TokenRecuperacion(String tokenHash, UUID usuarioId, Instant expiraEn, boolean usado) {}
    void crearRecuperacion(TokenRecuperacion t);
    Optional<TokenRecuperacion> buscarRecuperacion(String tokenHash);
    void marcarRecuperacionUsada(String tokenHash);
}
