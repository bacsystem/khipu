package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PlanSolicitadoRepository;

import java.util.UUID;

/** El plan que pidió la cuenta al registrarse (#581, V74). Solo esta columna: el guardado de la cuenta no la toca, y esto no toca las demás. */
@RequiredArgsConstructor
public class JdbcPlanSolicitadoRepository implements PlanSolicitadoRepository {
    private final JdbcTemplate jdbc;

    @Override public void anotar(UUID cuentaId, UUID planId) {
        jdbc.update("UPDATE cuenta SET plan_solicitado_id = ? WHERE id = ?", planId, cuentaId);
    }
}
