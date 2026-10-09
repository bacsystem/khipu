package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.SesionRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcSesionRepository implements SesionRepository {
    private final JdbcTemplate jdbc;

    @Override public void crear(Sesion s) {
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, revocada) VALUES (?, ?, ?, ?, ?)",
                s.id(), s.usuarioId(), s.refreshHash(), Timestamp.from(s.expiraEn()), s.revocada());
    }
    private static final String COLUMNAS = "SELECT id, usuario_id, refresh_hash, expira_en, revocada, rotada_en, reemplazada_por FROM sesion";
    private static final RowMapper<Sesion> MAPPER = (rs, i) -> {
        Timestamp rotada = rs.getTimestamp("rotada_en");
        return new Sesion(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getString("refresh_hash"),
                rs.getTimestamp("expira_en").toInstant(), rs.getBoolean("revocada"), rotada == null ? null : rotada.toInstant(), rs.getObject("reemplazada_por", UUID.class));
    };

    @Override public Optional<Sesion> buscarPorRefreshHash(String hash) {
        return jdbc.query(COLUMNAS + " WHERE refresh_hash = ?", MAPPER, hash).stream().findFirst();
    }
    @Override public Optional<Sesion> buscar(UUID id) {
        return jdbc.query(COLUMNAS + " WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    // Revocar quita la marca de rotación: así un logout o restablecer la contraseña no deja gracia (S6).
    @Override public void revocar(UUID id) { jdbc.update("UPDATE sesion SET revocada = true, rotada_en = NULL, reemplazada_por = NULL WHERE id = ?", id); }
    @Override public void revocarTodas(UUID usuarioId) {
        jdbc.update("UPDATE sesion SET revocada = true, rotada_en = NULL, reemplazada_por = NULL WHERE usuario_id = ?", usuarioId);
    }
    @Override public void rotar(UUID id, UUID nueva, Instant en) {
        jdbc.update("UPDATE sesion SET revocada = true, rotada_en = COALESCE(rotada_en, ?), reemplazada_por = COALESCE(reemplazada_por, ?) WHERE id = ?",
                Timestamp.from(en), nueva, id);
    }

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
