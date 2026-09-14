package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SesionRepository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcSesionRepository implements SesionRepository {
    private final JdbcTemplate jdbc;

    @Override public void crear(Sesion s) {
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, revocada) VALUES (?, ?, ?, ?, ?)",
                s.id(), s.usuarioId(), s.refreshHash(), Timestamp.from(s.expiraEn()), s.revocada());
    }
    @Override public Optional<Sesion> buscarPorRefreshHash(String hash) {
        return jdbc.query("SELECT id, usuario_id, refresh_hash, expira_en, revocada FROM sesion WHERE refresh_hash = ?",
                (rs, i) -> new Sesion(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getString("refresh_hash"),
                        rs.getTimestamp("expira_en").toInstant(), rs.getBoolean("revocada")), hash).stream().findFirst();
    }
    @Override public void revocar(UUID id) { jdbc.update("UPDATE sesion SET revocada = true WHERE id = ?", id); }
    @Override public void revocarTodas(UUID usuarioId) { jdbc.update("UPDATE sesion SET revocada = true WHERE usuario_id = ?", usuarioId); }

    @Override public void crearRecuperacion(TokenRecuperacion t) {
        jdbc.update("INSERT INTO token_recuperacion (token_hash, usuario_id, expira_en, usado) VALUES (?, ?, ?, ?)",
                t.tokenHash(), t.usuarioId(), Timestamp.from(t.expiraEn()), t.usado());
    }
    @Override public Optional<TokenRecuperacion> buscarRecuperacion(String tokenHash) {
        return jdbc.query("SELECT token_hash, usuario_id, expira_en, usado FROM token_recuperacion WHERE token_hash = ?",
                (rs, i) -> new TokenRecuperacion(rs.getString("token_hash"), rs.getObject("usuario_id", UUID.class),
                        rs.getTimestamp("expira_en").toInstant(), rs.getBoolean("usado")), tokenHash).stream().findFirst();
    }
    @Override public void marcarRecuperacionUsada(String tokenHash) { jdbc.update("UPDATE token_recuperacion SET usado = true WHERE token_hash = ?", tokenHash); }
}
