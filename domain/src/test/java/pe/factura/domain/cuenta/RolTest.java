package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** #21: lo que puede cada rol. */
class RolTest {
    @Test void elAdministradorPuedeTodo() {
        for (Permiso p : Permiso.values()) assertThat(Rol.ADMIN.puede(p)).as(p.name()).isTrue();
    }

    @Test void elEmisorMiraYEmitePeroNoConfiguraNiGestionaElEquipo() {
        assertThat(Rol.EMISOR.puede(Permiso.LEER)).isTrue();
        assertThat(Rol.EMISOR.puede(Permiso.EMITIR)).isTrue();
        assertThat(Rol.EMISOR.puede(Permiso.CONFIGURAR)).isFalse();
        assertThat(Rol.EMISOR.puede(Permiso.GESTIONAR_EQUIPO)).isFalse();
    }

    @Test void elDeSoloLecturaSoloMira() {
        assertThat(Rol.LECTURA.puede(Permiso.LEER)).isTrue();
        assertThat(Rol.LECTURA.puede(Permiso.EMITIR)).isFalse();
        assertThat(Rol.LECTURA.puede(Permiso.CONFIGURAR)).isFalse();
        assertThat(Rol.LECTURA.puede(Permiso.GESTIONAR_EQUIPO)).isFalse();
    }

    @Test void cadaRolTieneSuEtiqueta() {
        assertThat(Rol.ADMIN.etiqueta()).isEqualTo("Administrador");
        assertThat(Rol.EMISOR.etiqueta()).isEqualTo("Emisor");
        assertThat(Rol.LECTURA.etiqueta()).isEqualTo("Solo lectura");
    }
}
