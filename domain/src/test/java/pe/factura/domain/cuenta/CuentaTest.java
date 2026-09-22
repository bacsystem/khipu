package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Celular de contacto en Perú: 9 dígitos que empiezan con 9; opcional a nivel de dominio (cuentas anteriores a este campo). */
class CuentaTest {
    private static Cuenta con(String telefono) {
        return new Cuenta(UUID.randomUUID(), "Mi negocio", "a@b.pe", telefono);
    }

    @Test void sinTelefonoQuedaNulo() {
        assertThat(con(null).telefono()).isNull();
        assertThat(con("  ").telefono()).isNull();
        assertThat(new Cuenta(UUID.randomUUID(), "Mi negocio", "a@b.pe").telefono()).isNull();
    }

    @Test void aceptaConYSinPrefijoYNormalizaEspaciosYGuiones() {
        assertThat(con("987654321").telefono()).isEqualTo("987654321");
        assertThat(con("+51987654321").telefono()).isEqualTo("987654321");
        assertThat(con("51987654321").telefono()).isEqualTo("987654321");
        assertThat(con("+51 987 654 321").telefono()).isEqualTo("987654321");
        assertThat(con("987-654-321").telefono()).isEqualTo("987654321");
    }

    @Test void rechazaFormatoInvalido() {
        assertThatThrownBy(() -> con("123456789")).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
        assertThatThrownBy(() -> con("98765432")).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");   // 8 dígitos
        assertThatThrownBy(() -> con("9876543210")).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");  // 10 dígitos
        assertThatThrownBy(() -> con("987654abc")).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
    }
}
