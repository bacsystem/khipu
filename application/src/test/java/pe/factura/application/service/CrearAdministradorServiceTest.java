package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.Administrador;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrearAdministradorServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.clavePlataforma("203.0.113.7");

    Map<UUID, Administrador> map = new HashMap<>();
    AdministradorRepository administradores = new AdministradorRepository() {
        public void guardar(Administrador a) { map.put(a.id(), a); }
        public Optional<Administrador> buscar(UUID id) { return Optional.ofNullable(map.get(id)); }
        public Optional<Administrador> buscarPorEmail(String e) { return map.values().stream().filter(a -> a.email().equals(e)).findFirst(); }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    CrearAdministradorService service = new CrearAdministradorService(administradores, hasher, uow, auditoria, Fakes.CLOCK);

    @Test void creaConEmailNormalizadoYPasswordHasheada() {
        Administrador a = service.crear(ACTOR, "Ana@Khipu.PE", "Segura123");
        assertThat(a.email()).isEqualTo("ana@khipu.pe");
        assertThat(a.passwordHash()).isEqualTo("H(Segura123)");
        assertThat(a.activo()).isTrue();
        assertThat(map).containsKey(a.id());
    }

    @Test void rechazaDuplicadoYPasswordDebil() {
        service.crear(ACTOR, "ana@khipu.pe", "Segura123");
        assertThatThrownBy(() -> service.crear(ACTOR, "ANA@KHIPU.PE", "Otra12345")).extracting("codigo").isEqualTo("DUPLICADO");
        assertThatThrownBy(() -> service.crear(ACTOR, "otro@khipu.pe", "corta")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @Test void dejaEnLaBitacoraQuienCreoAlAdministrador() {
        service.crear(ACTOR, "Ana@Khipu.PE", "Segura123");

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria reg = auditoria.registros.get(0);
        assertThat(reg.actor()).isEqualTo(ACTOR);
        assertThat(reg.accion()).isEqualTo(AccionAdmin.CREAR_ADMINISTRADOR);
        assertThat(reg.cuentaId()).isNull();
        assertThat(reg.tenantId()).isNull();
        assertThat(reg.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(reg.detalle()).contains("ana@khipu.pe");
    }

    @Test void laBitacoraNuncaLlevaLaContrasena() {
        service.crear(ACTOR, "ana@khipu.pe", "Segura123");
        assertThat(auditoria.registros.get(0).detalle()).doesNotContain("Segura123").doesNotContain("H(");
    }

    @Test void guardaAlAdministradorYEscribeLaBitacoraEnLaMismaTransaccion() {
        service.crear(ACTOR, "ana@khipu.pe", "Segura123");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void siLaBitacoraFallaLaAccionFalla() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");
        assertThatThrownBy(() -> service.crear(ACTOR, "ana@khipu.pe", "Segura123")).isInstanceOf(IllegalStateException.class);
    }

    @Test void unaAccionRechazadaNoDejaRegistro() {
        service.crear(ACTOR, "ana@khipu.pe", "Segura123");
        assertThatThrownBy(() -> service.crear(ACTOR, "ana@khipu.pe", "Otra12345")).extracting("codigo").isEqualTo("DUPLICADO");
        assertThatThrownBy(() -> service.crear(ACTOR, "otro@khipu.pe", "corta")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThat(auditoria.registros).hasSize(1);
    }
}
