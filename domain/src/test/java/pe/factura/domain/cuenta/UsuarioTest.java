package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UsuarioTest {
    @Test void normalizaEmail() {
        Usuario u = new Usuario(UUID.randomUUID(), UUID.randomUUID(), "  Ana@Empresa.PE ", "hash", Rol.ADMIN, true);
        assertThat(u.email()).isEqualTo("ana@empresa.pe");
        assertThat(u.esAdmin()).isTrue();
    }
    @Test void emailInvalido() {
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), UUID.randomUUID(), "no-es-correo", "h", Rol.ADMIN, true))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("EMAIL_INVALIDO");
    }
    @Test void politicaDePassword() {
        assertThatThrownBy(() -> Usuario.validarPassword("corta1")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThatThrownBy(() -> Usuario.validarPassword("sinnumeros")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThatThrownBy(() -> Usuario.validarPassword("12345678")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");  // sin letras
        assertThatCode(() -> Usuario.validarPassword("Segura123")).doesNotThrowAnyException();
    }
    @Test void cuentaExigeNombre() {
        assertThatThrownBy(() -> new Cuenta(UUID.randomUUID(), " ", "a@b.pe")).extracting("codigo").isEqualTo("NOMBRE_REQUERIDO");
        assertThat(new Cuenta(UUID.randomUUID(), "Mi negocio", "A@B.PE").email()).isEqualTo("a@b.pe");
    }
}
