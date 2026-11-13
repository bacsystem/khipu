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
     * <p>
     * {@code creadaEn}: cuándo nació la fila, con el reloj de la aplicación (no el {@code now()} de la base). Cada login y cada refresh inserta una fila
     * nueva, así que es el último refresco de esa rama y de ahí se mide la inactividad (#636).
     */
    record Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada, Instant rotadaEn, UUID reemplazadaPor, UUID familia,
                  Instant creadaEn) {
        /** Una sesión nueva de un login: su propia familia. */
        public Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada, Instant creadaEn) {
            this(id, usuarioId, refreshHash, expiraEn, revocada, null, null, id, creadaEn);
        }
        /** Una sesión nueva que sale de un refresh: la familia de la que rota. */
        public Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, UUID familia, Instant creadaEn) {
            this(id, usuarioId, refreshHash, expiraEn, false, null, null, familia, creadaEn);
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
    /**
     * Cierra la sesión porque un refresh la reemplazó por {@code nueva}. Si ya estaba rotada, conserva la primera rotación.
     * Devuelve {@code false}, sin tocar nada, si la sesión quedó revocada por otro motivo (logout, restablecer la contraseña, reuso) sin haber sido
     * rotada: no hay nada que rotar y quien llama debe rechazar el refresh.
     */
    boolean rotar(UUID id, UUID nueva, Instant en);

    record TokenRecuperacion(String tokenHash, UUID usuarioId, Instant expiraEn, boolean usado) {}
    void crearRecuperacion(TokenRecuperacion t);
    Optional<TokenRecuperacion> buscarRecuperacion(String tokenHash);
    void marcarRecuperacionUsada(String tokenHash);
}
