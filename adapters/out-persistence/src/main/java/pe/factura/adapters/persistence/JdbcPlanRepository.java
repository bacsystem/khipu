package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lectura de los planes (#189). Un límite en {@code NULL} es «sin límite»; la base no admite cero, así que {@link Limite} nunca falla al leer. */
@RequiredArgsConstructor
public class JdbcPlanRepository implements PlanRepository {
    private final JdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT id, nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, estado, por_defecto FROM plan""";

    @Override public Optional<Plan> buscar(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::mapear, id).stream().findFirst();
    }

    @Override public List<Plan> listar() {
        return jdbc.query(SELECT + " ORDER BY precio_mensual, nombre", this::mapear);
    }

    @Override public Plan porDefecto() {
        return jdbc.query(SELECT + " WHERE por_defecto", this::mapear).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay plan por defecto: la migración de planes debía dejarlo"));
    }

    private Plan mapear(ResultSet rs, int i) throws SQLException {
        return new Plan(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getBigDecimal("precio_mensual"),
                limite(rs, "documentos_al_mes"), rs.getInt("rucs"), limite(rs, "usuarios"), limite(rs, "api_keys"),
                rs.getInt("retencion_anios"), EstadoPlan.valueOf(rs.getString("estado")), rs.getBoolean("por_defecto"));
    }

    private static Limite limite(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? Limite.sinLimite() : Limite.de(valor);
    }
}
