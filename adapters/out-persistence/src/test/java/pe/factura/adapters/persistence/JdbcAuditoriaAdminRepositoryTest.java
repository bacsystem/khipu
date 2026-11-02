package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAuditoriaAdminRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    JdbcAuditoriaAdminRepository repo = new JdbcAuditoriaAdminRepository(jdbc);

    @Test void guardaQuienQueSobreQueYCuando() {
        UUID administrador = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID cuenta = UUID.randomUUID();
        RegistroAuditoria r = RegistroAuditoria.de(ActorAdmin.administrador(administrador, "203.0.113.7"),
                AccionAdmin.CREAR_TENANT, cuenta, tenant, "ruc=20100066603 entorno=BETA", AHORA);

        repo.registrar(r);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM auditoria_admin WHERE id = ?", r.id());
        assertThat(fila.get("actor_tipo")).isEqualTo("ADMINISTRADOR");
        assertThat(fila.get("administrador_id")).isEqualTo(administrador);
        assertThat(fila.get("accion")).isEqualTo("CREAR_TENANT");
        assertThat(fila.get("cuenta_id")).isEqualTo(cuenta);
        assertThat(fila.get("tenant_id")).isEqualTo(tenant);
        assertThat(fila.get("detalle")).isEqualTo("ruc=20100066603 entorno=BETA");
        assertThat(fila.get("ip")).isEqualTo("203.0.113.7");
        assertThat(((Timestamp) fila.get("ocurrido_en")).toInstant()).isEqualTo(AHORA);
    }

    @Test void laClaveDePlataformaQuedaSinAdministradorYLosOpcionalesEnNull() {
        RegistroAuditoria r = RegistroAuditoria.de(ActorAdmin.clavePlataforma("198.51.100.4"), AccionAdmin.CREAR_ADMINISTRADOR, null, null, null, AHORA);

        repo.registrar(r);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM auditoria_admin WHERE id = ?", r.id());
        assertThat(fila.get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(fila.get("administrador_id")).isNull();
        assertThat(fila.get("cuenta_id")).isNull();
        assertThat(fila.get("tenant_id")).isNull();
        assertThat(fila.get("detalle")).isNull();
    }

    @Test void guardaUnaFilaPorRegistro() {
        ActorAdmin actor = ActorAdmin.clavePlataforma("198.51.100.4");
        repo.registrar(RegistroAuditoria.de(actor, AccionAdmin.CREAR_TENANT, null, null, null, AHORA));
        repo.registrar(RegistroAuditoria.de(actor, AccionAdmin.CREAR_TENANT, null, null, null, AHORA));
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT id FROM auditoria_admin");
        assertThat(filas).hasSize(2);
    }

    @Test void laBaseRechazaUnActorIncoherente() {
        // Un «administrador» sin id, o una «clave de plataforma» con id, no explicarían quién actuó: se rechaza aunque alguien salte el dominio.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO auditoria_admin (id, actor_tipo, administrador_id, accion, ip, ocurrido_en) VALUES (?, 'ADMINISTRADOR', NULL, 'CREAR_TENANT', '1.1.1.1', now())", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO auditoria_admin (id, actor_tipo, administrador_id, accion, ip, ocurrido_en) VALUES (?, 'CLAVE_PLATAFORMA', ?, 'CREAR_TENANT', '1.1.1.1', now())", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO auditoria_admin (id, actor_tipo, administrador_id, accion, ip, ocurrido_en) VALUES (?, 'OTRO', NULL, 'CREAR_TENANT', '1.1.1.1', now())", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
