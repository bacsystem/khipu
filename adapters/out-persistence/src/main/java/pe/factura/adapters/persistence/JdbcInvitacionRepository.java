package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.InvitacionRepository;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** #21: invitaciones al equipo de una cuenta. */
@RequiredArgsConstructor
public class JdbcInvitacionRepository implements InvitacionRepository {
    private static final String COLS = "id, cuenta_id, email, rol, token_hash, expira_en, estado, invitado_por, envios, created_at";
    private static final RowMapper<Invitacion> MAPPER = (rs, i) -> new Invitacion(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class),
            rs.getString("email"), Rol.valueOf(rs.getString("rol")), rs.getString("token_hash"), rs.getTimestamp("expira_en").toInstant(),
            Invitacion.Estado.valueOf(rs.getString("estado")), rs.getObject("invitado_por", UUID.class), rs.getInt("envios"), rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    @Override public void crear(Invitacion i) {
        jdbc.update("INSERT INTO invitacion (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", i.id(), i.cuentaId(), i.email(), i.rol().name(), i.tokenHash(),
                Timestamp.from(i.expiraEn()), i.estado().name(), i.invitadoPor(), i.envios(), Timestamp.from(i.creadaEn()));
    }

    /** Solo lo que cambia un reenvío o una revocación, y solo si sigue pendiente. */
    @Override public boolean actualizarPendiente(Invitacion i) {
        return jdbc.update("UPDATE invitacion SET token_hash = ?, expira_en = ?, estado = ?, envios = ? WHERE id = ? AND estado = 'PENDIENTE'",
                i.tokenHash(), Timestamp.from(i.expiraEn()), i.estado().name(), i.envios(), i.id()) == 1;
    }

    @Override public Optional<Invitacion> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM invitacion WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override public Optional<Invitacion> buscarPorTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + COLS + " FROM invitacion WHERE token_hash = ?", MAPPER, tokenHash).stream().findFirst();
    }

    @Override public List<Invitacion> pendientes(UUID cuentaId) {
        return jdbc.query("SELECT " + COLS + " FROM invitacion WHERE cuenta_id = ? AND estado = 'PENDIENTE' ORDER BY created_at DESC, email", MAPPER, cuentaId);
    }

    /** Las condiciones en el UPDATE: de dos aceptaciones a la vez, o de una que cruza un reenvío, solo pasa la que encuentra la fila como la vio. */
    @Override public boolean aceptar(UUID id, String tokenHash, Instant ahora) {
        return jdbc.update("UPDATE invitacion SET estado = 'ACEPTADA', aceptada_en = ? WHERE id = ? AND token_hash = ? AND estado = 'PENDIENTE' AND expira_en > ?",
                Timestamp.from(ahora), id, tokenHash, Timestamp.from(ahora)) == 1;
    }
}
