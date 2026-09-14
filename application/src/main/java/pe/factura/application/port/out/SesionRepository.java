package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sesiones de refresh y tokens de recuperación (se almacenan solo hashes). */
public interface SesionRepository {
    record Sesion(UUID id, UUID usuarioId, String refreshHash, Instant expiraEn, boolean revocada) {}
    void crear(Sesion s);
    Optional<Sesion> buscarPorRefreshHash(String hash);
    void revocar(UUID id);
    void revocarTodas(UUID usuarioId);

    record TokenRecuperacion(String tokenHash, UUID usuarioId, Instant expiraEn, boolean usado) {}
    void crearRecuperacion(TokenRecuperacion t);
    Optional<TokenRecuperacion> buscarRecuperacion(String tokenHash);
    void marcarRecuperacionUsada(String tokenHash);
}
