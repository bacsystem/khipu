package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AdministradorTest {
    @Test void normalizaEmail() {
        Administrador a = new Administrador(UUID.randomUUID(), "  Ana@Khipu.PE ", "hash", true);
        assertThat(a.email()).isEqualTo("ana@khipu.pe");
    }

    @Test void emailInvalido() {
        assertThatThrownBy(() -> new Administrador(UUID.randomUUID(), "no-es-correo", "h", true))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("EMAIL_INVALIDO");
    }

    @Test void passwordHashRequerido() {
        assertThatThrownBy(() -> new Administrador(UUID.randomUUID(), "a@b.pe", " ", true))
                .extracting("codigo").isEqualTo("PASSWORD_REQUERIDO");
    }

    @Test void politicaDePassword() {
        assertThatThrownBy(() -> Administrador.validarPassword("corta1")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThatThrownBy(() -> Administrador.validarPassword("sinnumeros")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThatThrownBy(() -> Administrador.validarPassword("12345678")).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
        assertThatCode(() -> Administrador.validarPassword("Segura123")).doesNotThrowAnyException();
    }
}
