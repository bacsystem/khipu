package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.SuspenderCuentaUseCase.EstadoDeCuenta;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.SuspensionRepository;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuspenderCuentaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");

    UUID cuentaId = UUID.randomUUID();
    Map<UUID, Cuenta> cuentasGuardadas = new HashMap<>(Map.of());
    {
        cuentasGuardadas.put(cuentaId, new Cuenta(cuentaId, "Mi negocio", "ana@negocio.pe"));
    }

    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { throw new AssertionError("suspender no reescribe la cuenta"); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasGuardadas.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String email) { throw new AssertionError("no se busca por correo"); }
    };

    /** En memoria, con la misma regla atómica del real: cambia solo si el estado era el contrario. */
    static class Suspensiones implements SuspensionRepository {
        final Map<UUID, Instant> suspendidas = new HashMap<>();
        public boolean cuentaSuspendida(UUID c) { return suspendidas.containsKey(c); }
        public boolean empresaSuspendida(UUID t) { throw new AssertionError("el servicio trabaja por cuenta"); }
        public boolean suspender(UUID c, Instant cuando) { return suspendidas.putIfAbsent(c, cuando) == null; }
        public boolean reactivar(UUID c) { return suspendidas.remove(c) != null; }
    }

    Suspensiones suspensiones = new Suspensiones();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    SuspenderCuentaService service = new SuspenderCuentaService(cuentas, suspensiones, auditoria, uow, Fakes.CLOCK);

    @Test void suspenderMarcaLaCuentaConLaHoraDelServidor() {
        EstadoDeCuenta e = service.suspender(ACTOR, cuentaId, null);

        assertThat(e.cuentaId()).isEqualTo(cuentaId);
        assertThat(e.suspendida()).isTrue();
        assertThat(e.suspendidaEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(suspensiones.cuentaSuspendida(cuentaId)).isTrue();
    }

    @Test void reactivarLaDejaComoEstaba() {
        service.suspender(ACTOR, cuentaId, null);

        EstadoDeCuenta e = service.reactivar(ACTOR, cuentaId);

        assertThat(e.suspendida()).isFalse();
        assertThat(e.suspendidaEn()).isNull();
        assertThat(suspensiones.cuentaSuspendida(cuentaId)).isFalse();
    }

    @Test void nadaSeBorraAlSuspenderNiAlReactivar() {
        service.suspender(ACTOR, cuentaId, "no pagó");
        service.reactivar(ACTOR, cuentaId);

        assertThat(cuentasGuardadas).containsKey(cuentaId);
    }

    @Test void suspenderQuedaEnLaBitacoraConElMotivoYQuienLoHizo() {
        service.suspender(ACTOR, cuentaId, "  Factura de septiembre sin pagar  ");

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.SUSPENDER_CUENTA);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isNull();
        assertThat(r.detalle()).isEqualTo("motivo=Factura de septiembre sin pagar");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void sinMotivoLaBitacoraNoInventaUno() {
        service.suspender(ACTOR, cuentaId, "   ");

        assertThat(auditoria.registros.get(0).detalle()).isNull();
    }

    @Test void reactivarTambienQuedaEnLaBitacora() {
        service.suspender(ACTOR, cuentaId, null);

        service.reactivar(ACTOR, cuentaId);

        assertThat(auditoria.registros).hasSize(2);
        RegistroAuditoria r = auditoria.registros.get(1);
        assertThat(r.accion()).isEqualTo(AccionAdmin.REACTIVAR_CUENTA);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.actor()).isEqualTo(ACTOR);
    }

    @Test void elCambioYLaBitacoraVanEnLaMismaTransaccion() {
        service.suspender(ACTOR, cuentaId, null);
        service.reactivar(ACTOR, cuentaId);

        assertThat(auditoria.dentroAlRegistrar).containsExactly(true, true);
    }

    @Test void siLaBitacoraFallaLaAccionFalla() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.suspender(ACTOR, cuentaId, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test void suspenderUnaCuentaYaSuspendidaEsConflictoYNoDejaOtroRegistro() {
        service.suspender(ACTOR, cuentaId, null);

        assertThatThrownBy(() -> service.suspender(ACTOR, cuentaId, null)).extracting("codigo").isEqualTo("CUENTA_YA_SUSPENDIDA");
        assertThat(auditoria.registros).hasSize(1);
    }

    @Test void reactivarUnaCuentaActivaEsConflictoYNoDejaRegistro() {
        assertThatThrownBy(() -> service.reactivar(ACTOR, cuentaId)).extracting("codigo").isEqualTo("CUENTA_NO_SUSPENDIDA");
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unaCuentaQueNoExisteEsNoEncontradaEnAmbasAcciones() {
        UUID otra = UUID.randomUUID();

        assertThatThrownBy(() -> service.suspender(ACTOR, otra, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.reactivar(ACTOR, otra)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.suspender(ACTOR, null, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(suspensiones.suspendidas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unMotivoDemasiadoLargoSeRechazaSinSuspender() {
        String largo = "x".repeat(201);

        assertThatThrownBy(() -> service.suspender(ACTOR, cuentaId, largo)).extracting("codigo").isEqualTo("MOTIVO_INVALIDO");
        assertThat(suspensiones.suspendidas).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unMotivoDeExactamenteElMaximoSeAcepta() {
        service.suspender(ACTOR, cuentaId, "x".repeat(200));

        assertThat(suspensiones.cuentaSuspendida(cuentaId)).isTrue();
    }
}
