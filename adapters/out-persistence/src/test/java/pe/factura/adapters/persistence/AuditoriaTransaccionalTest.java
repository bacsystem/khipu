package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.service.AdministrarTenantService;
import pe.factura.application.service.CrearAdministradorService;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.Entorno;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La garantía de #178: la bitácora se escribe en la misma transacción que la acción, así que si la bitácora falla la
 * acción no queda hecha. Con fakes en memoria esto no se puede probar (no hay rollback): hace falta Postgres.
 */
class AuditoriaTransaccionalTest extends PersistenciaTestBase {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Lima"));
    SecretCipher sinCifrar = new SecretCipher() { public byte[] cifrar(byte[] p) { return p; } public byte[] descifrar(byte[] c) { return c; } };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    AuditoriaAdminRepository bitacoraRota = (RegistroAuditoria r) -> { throw new IllegalStateException("bitácora no disponible"); };
    AuditoriaAdminRepository bitacoraReal = new JdbcAuditoriaAdminRepository(jdbc);

    AdministrarTenantService tenantService(AuditoriaAdminRepository auditoria) {
        return new AdministrarTenantService(new JdbcTenantRepository(jdbc, sinCifrar), new JdbcSerieRepository(jdbc), new JdbcApiKeyRepository(jdbc),
                uow, "pepper", CLOCK, new JdbcEstablecimientoRepository(jdbc), auditoria);
    }

    CrearAdministradorService administradorService(AuditoriaAdminRepository auditoria) {
        return new CrearAdministradorService(new JdbcAdministradorRepository(jdbc), hasher, uow, auditoria, CLOCK);
    }

    long filas(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    @Test void siLaBitacoraFallaElTenantNoSeCrea() {
        assertThatThrownBy(() -> tenantService(bitacoraRota).crearTenant(ACTOR, "20100066603", "EMPRESA SAC", Entorno.BETA))
                .isInstanceOf(IllegalStateException.class);

        assertThat(filas("tenant")).isZero();
        assertThat(filas("api_key")).isZero();
    }

    @Test void conLaBitacoraSanaElTenantYSuRegistroQuedanJuntos() {
        UUID tenantId = tenantService(bitacoraReal).crearTenant(ACTOR, "20100066603", "EMPRESA SAC", Entorno.BETA).tenant().id();

        assertThat(filas("tenant")).isEqualTo(1);
        var fila = jdbc.queryForMap("SELECT * FROM auditoria_admin");
        assertThat(fila.get("accion")).isEqualTo("CREAR_TENANT");
        assertThat(fila.get("tenant_id")).isEqualTo(tenantId);
        assertThat(fila.get("administrador_id")).isEqualTo(ACTOR.administradorId());
    }

    @Test void siLaBitacoraFallaElAdministradorNoSeCrea() {
        assertThatThrownBy(() -> administradorService(bitacoraRota).crear(ACTOR, "ana@khipu.pe", "Segura123"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(filas("administrador")).isZero();
    }

    @Test void conLaBitacoraSanaElAdministradorYSuRegistroQuedanJuntos() {
        administradorService(bitacoraReal).crear(ACTOR, "ana@khipu.pe", "Segura123");

        assertThat(filas("administrador")).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM auditoria_admin").get("accion")).isEqualTo("CREAR_ADMINISTRADOR");
    }
}
