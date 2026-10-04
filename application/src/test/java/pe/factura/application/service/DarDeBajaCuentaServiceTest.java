package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase.EstadoDeBaja;
import pe.factura.application.port.out.BajaDeCuentaRepository;
import pe.factura.application.port.out.CuentaRepository;
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

/** La baja lógica de un cliente (#201): no borra nada, es reversible y deja su registro en la misma transacción. */
class DarDeBajaCuentaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");

    UUID cuentaId = UUID.randomUUID();
    Map<UUID, Cuenta> cuentasGuardadas = new HashMap<>();
    {
        cuentasGuardadas.put(cuentaId, new Cuenta(cuentaId, "Mi negocio", "ana@negocio.pe"));
    }

    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { throw new AssertionError("la baja no reescribe la cuenta"); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasGuardadas.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String email) { throw new AssertionError("no se busca por correo"); }
    };

    /** En memoria, con la misma regla atómica del real: cambia solo si el estado era el contrario. */
    static class Bajas implements BajaDeCuentaRepository {
        final Map<UUID, Instant> deBaja = new HashMap<>();
        public boolean darDeBaja(UUID c, Instant cuando) { return deBaja.putIfAbsent(c, cuando) == null; }
        public boolean reponer(UUID c) { return deBaja.remove(c) != null; }
    }

    Bajas bajas = new Bajas();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    DarDeBajaCuentaService service = new DarDeBajaCuentaService(cuentas, bajas, auditoria, uow, Fakes.CLOCK);

    @Test void darDeBajaMarcaLaCuentaConLaHoraDelServidor() {
        EstadoDeBaja e = service.darDeBaja(ACTOR, cuentaId, null);

        assertThat(e.cuentaId()).isEqualTo(cuentaId);
        assertThat(e.deBaja()).isTrue();
        assertThat(e.bajaEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(bajas.deBaja).containsEntry(cuentaId, Fakes.CLOCK.instant());
    }

    @Test void reponerLaDejaComoEstaba() {
        service.darDeBaja(ACTOR, cuentaId, null);

        EstadoDeBaja e = service.reponer(ACTOR, cuentaId);

        assertThat(e.deBaja()).isFalse();
        assertThat(e.bajaEn()).isNull();
        assertThat(bajas.deBaja).isEmpty();
    }

    /** Lo que la ley obliga a conservar: la baja marca la cuenta, no toca ninguna fila. */
    @Test void nadaSeBorraAlDarDeBajaNiAlReponer() {
        service.darDeBaja(ACTOR, cuentaId, "se fue");
        service.reponer(ACTOR, cuentaId);

        assertThat(cuentasGuardadas).containsKey(cuentaId);
    }

    @Test void darDeBajaQuedaEnLaBitacoraConElMotivoYQuienLoHizo() {
        service.darDeBaja(ACTOR, cuentaId, "  Cerró su negocio  ");

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.DAR_DE_BAJA_CUENTA);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isNull();
        assertThat(r.detalle()).isEqualTo("motivo=Cerró su negocio");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void sinMotivoLaBitacoraNoInventaUno() {
        service.darDeBaja(ACTOR, cuentaId, "   ");

        assertThat(auditoria.registros.get(0).detalle()).isNull();
    }

    @Test void reponerTambienQuedaEnLaBitacora() {
        service.darDeBaja(ACTOR, cuentaId, null);

        service.reponer(ACTOR, cuentaId);

        assertThat(auditoria.registros).hasSize(2);
        RegistroAuditoria r = auditoria.registros.get(1);
        assertThat(r.accion()).isEqualTo(AccionAdmin.REPONER_CUENTA);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.actor()).isEqualTo(ACTOR);
    }

    @Test void elCambioYLaBitacoraVanEnLaMismaTransaccion() {
        service.darDeBaja(ACTOR, cuentaId, null);
        service.reponer(ACTOR, cuentaId);

        assertThat(auditoria.dentroAlRegistrar).containsExactly(true, true);
    }

    @Test void siLaBitacoraFallaLaAccionFalla() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.darDeBaja(ACTOR, cuentaId, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test void darDeBajaUnaCuentaYaDeBajaEsConflictoYNoDejaOtroRegistro() {
        service.darDeBaja(ACTOR, cuentaId, null);

        assertThatThrownBy(() -> service.darDeBaja(ACTOR, cuentaId, null)).extracting("codigo").isEqualTo("CUENTA_YA_DE_BAJA");
        assertThat(auditoria.registros).hasSize(1);
    }

    @Test void reponerUnaCuentaQueNoEstaDeBajaEsConflictoYNoDejaRegistro() {
        assertThatThrownBy(() -> service.reponer(ACTOR, cuentaId)).extracting("codigo").isEqualTo("CUENTA_NO_DE_BAJA");
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unaCuentaQueNoExisteEsNoEncontradaEnAmbasAcciones() {
        UUID otra = UUID.randomUUID();

        assertThatThrownBy(() -> service.darDeBaja(ACTOR, otra, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.reponer(ACTOR, otra)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.darDeBaja(ACTOR, null, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(bajas.deBaja).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unMotivoDemasiadoLargoSeRechazaSinDarDeBaja() {
        String largo = "x".repeat(201);

        assertThatThrownBy(() -> service.darDeBaja(ACTOR, cuentaId, largo)).extracting("codigo").isEqualTo("MOTIVO_INVALIDO");
        assertThat(bajas.deBaja).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unMotivoDeExactamenteElMaximoSeAcepta() {
        service.darDeBaja(ACTOR, cuentaId, "x".repeat(200));

        assertThat(bajas.deBaja).containsKey(cuentaId);
    }
}
