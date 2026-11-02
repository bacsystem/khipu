package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.sql.Timestamp;

@RequiredArgsConstructor
public class JdbcAuditoriaAdminRepository implements AuditoriaAdminRepository {
    private final JdbcTemplate jdbc;

    @Override public void registrar(RegistroAuditoria r) {
        jdbc.update("""
            INSERT INTO auditoria_admin (id, actor_tipo, administrador_id, accion, cuenta_id, tenant_id, detalle, ip, ocurrido_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, r.id(), r.actor().tipo().name(), r.actor().administradorId(), r.accion().name(), r.cuentaId(), r.tenantId(),
                r.detalle(), r.actor().ip(), Timestamp.from(r.ocurridoEn()));
    }
}
