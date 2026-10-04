package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.VerificacionCorreoRepository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcVerificacionCorreoRepository implements VerificacionCorreoRepository {
    private final JdbcTemplate jdbc;

    @Override public void crear(Token t) {
        jdbc.update("INSERT INTO token_verificacion (token_hash, usuario_id, expira_en, usado) VALUES (?, ?, ?, ?)",
                t.tokenHash(), t.usuarioId(), Timestamp.from(t.expiraEn()), t.usado());
    }

    @Override public Optional<Token> buscar(String tokenHash) {
        return jdbc.query("SELECT token_hash, usuario_id, expira_en, usado FROM token_verificacion WHERE token_hash = ?",
                (rs, i) -> new Token(rs.getString("token_hash"), rs.getObject("usuario_id", UUID.class), rs.getTimestamp("expira_en").toInstant(), rs.getBoolean("usado")),
                tokenHash).stream().findFirst();
    }

    /** La condición {@code usado = false} en el UPDATE: dos clics simultáneos, solo uno lo marca. */
    @Override public boolean usar(String tokenHash) {
        return jdbc.update("UPDATE token_verificacion SET usado = true WHERE token_hash = ? AND usado = false", tokenHash) == 1;
    }
}
