package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroAuditoriaTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");

    @Test void guardaQuienQueSobreQueYCuando() {
        UUID tenant = UUID.randomUUID();
        RegistroAuditoria r = RegistroAuditoria.de(ACTOR, AccionAdmin.CREAR_TENANT, null, tenant, "ruc=20100066603", AHORA);
        assertThat(r.id()).isNotNull();
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.CREAR_TENANT);
        assertThat(r.cuentaId()).isNull();
        assertThat(r.tenantId()).isEqualTo(tenant);
        assertThat(r.detalle()).isEqualTo("ruc=20100066603");
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
    }

    @Test void cadaRegistroTieneSuPropioId() {
        RegistroAuditoria a = RegistroAuditoria.de(ACTOR, AccionAdmin.CREAR_TENANT, null, null, null, AHORA);
        RegistroAuditoria b = RegistroAuditoria.de(ACTOR, AccionAdmin.CREAR_TENANT, null, null, null, AHORA);
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test void sinActorAccionOInstanteNoSeRegistra() {
        assertThatThrownBy(() -> RegistroAuditoria.de(null, AccionAdmin.CREAR_TENANT, null, null, null, AHORA))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("AUDITORIA_INVALIDA");
        assertThatThrownBy(() -> RegistroAuditoria.de(ACTOR, null, null, null, null, AHORA))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("AUDITORIA_INVALIDA");
        assertThatThrownBy(() -> RegistroAuditoria.de(ACTOR, AccionAdmin.CREAR_TENANT, null, null, null, null))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("AUDITORIA_INVALIDA");
    }
}
