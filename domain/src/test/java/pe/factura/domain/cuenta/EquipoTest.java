package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #21: cambiar roles y desactivar usuarios sin dejar la cuenta sin un administrador activo. */
class EquipoTest {
    final UUID cuenta = UUID.randomUUID();
    final Usuario ana = usuario("ana@b.pe", Rol.ADMIN, true);
    final Usuario beto = usuario("beto@b.pe", Rol.EMISOR, true);
    final Usuario caro = usuario("caro@b.pe", Rol.LECTURA, true);

    private Usuario usuario(String email, Rol rol, boolean activo) {
        return new Usuario(UUID.randomUUID(), cuenta, email, "hash", rol, activo);
    }

    private Equipo equipo(Usuario... miembros) { return new Equipo(cuenta, List.of(miembros)); }

    private static void falla(Runnable r, String codigo) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo(codigo);
    }

    @Test void unAdministradorCambiaElRolDeOtro() {
        assertThat(equipo(ana, beto).cambiarRol(ana.id(), beto.id(), Rol.LECTURA).rol()).isEqualTo(Rol.LECTURA);
        assertThat(equipo(ana, caro).cambiarRol(ana.id(), caro.id(), Rol.ADMIN).rol()).isEqualTo(Rol.ADMIN);
    }

    @Test void soloUnAdministradorActivoGestionaElEquipo() {
        falla(() -> equipo(ana, beto, caro).cambiarRol(beto.id(), caro.id(), Rol.EMISOR), "ROL_INSUFICIENTE");
        falla(() -> equipo(ana, beto, caro).desactivar(caro.id(), beto.id()), "ROL_INSUFICIENTE");
        Usuario adminInactivo = usuario("dani@b.pe", Rol.ADMIN, false);
        falla(() -> equipo(ana, adminInactivo, beto).cambiarRol(adminInactivo.id(), beto.id(), Rol.ADMIN), "ROL_INSUFICIENTE");
        falla(() -> equipo(ana).exigirAdministrador(UUID.randomUUID()), "ROL_INSUFICIENTE");
    }

    @Test void elUnicoAdministradorNoPuedeDegradarseASiMismo() {
        falla(() -> equipo(ana, beto).cambiarRol(ana.id(), ana.id(), Rol.EMISOR), "ULTIMO_ADMINISTRADOR");
    }

    @Test void conOtroAdministradorActivoSiPuedeDegradarse() {
        Usuario otro = usuario("otro@b.pe", Rol.ADMIN, true);
        assertThat(equipo(ana, otro).cambiarRol(ana.id(), ana.id(), Rol.EMISOR).rol()).isEqualTo(Rol.EMISOR);
    }

    /** Un administrador desactivado no cuenta: no puede gestionar nada si lo reactivan después. */
    @Test void unAdministradorInactivoNoCuentaComoElOtroAdministrador() {
        Usuario inactivo = usuario("otro@b.pe", Rol.ADMIN, false);
        falla(() -> equipo(ana, inactivo).cambiarRol(ana.id(), ana.id(), Rol.LECTURA), "ULTIMO_ADMINISTRADOR");
    }

    @Test void nadieSeDesactivaASiMismo() {
        Usuario otro = usuario("otro@b.pe", Rol.ADMIN, true);
        falla(() -> equipo(ana, otro).desactivar(ana.id(), ana.id()), "NO_PUEDES_DESACTIVARTE");
    }

    /** El último administrador activo solo podría desactivarse a sí mismo, y eso ya no se puede: entre dos, uno desactiva al otro. */
    @Test void unAdministradorDesactivaAOtroAdministrador() {
        Usuario otro = usuario("otro@b.pe", Rol.ADMIN, true);
        Equipo e = equipo(ana, otro);
        assertThat(e.desactivar(ana.id(), otro.id()).activo()).isFalse();
    }

    @Test void desactivarYReactivar() {
        Usuario desactivado = equipo(ana, beto).desactivar(ana.id(), beto.id());
        assertThat(desactivado.activo()).isFalse();
        assertThat(desactivado.rol()).isEqualTo(Rol.EMISOR);
        assertThat(equipo(ana, desactivado).reactivar(ana.id(), beto.id()).activo()).isTrue();
    }

    @Test void unUsuarioDeOtraCuentaNoSeEncuentra() {
        falla(() -> equipo(ana).cambiarRol(ana.id(), UUID.randomUUID(), Rol.EMISOR), "NO_ENCONTRADO");
        falla(() -> equipo(ana).desactivar(ana.id(), UUID.randomUUID()), "NO_ENCONTRADO");
    }

    @Test void unEquipoNoAdmiteMiembrosDeOtraCuenta() {
        Usuario ajeno = new Usuario(UUID.randomUUID(), UUID.randomUUID(), "x@b.pe", "hash", Rol.ADMIN, true);
        assertThatThrownBy(() -> equipo(ana, ajeno)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void elMismoRolNoCambiaNada() {
        assertThat(equipo(ana, beto).cambiarRol(ana.id(), beto.id(), Rol.EMISOR)).isEqualTo(beto);
    }

    @Test void reconoceElCorreoDeUnMiembroSinImportarMayusculas() {
        assertThat(equipo(ana, beto).tieneCorreo(" BETO@b.pe ")).isTrue();
        assertThat(equipo(ana, beto).tieneCorreo("otro@b.pe")).isFalse();
    }
}
