package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RucTest {
    @Test void digitoVerificadorModulo11() {
        assertThat(Ruc.esValido("20100066603")).isTrue();   // RUC de ejemplo de SUNAT
        assertThat(Ruc.esValido("20131312955")).isTrue();   // SUNAT
        assertThat(Ruc.esValido("20601234565")).isTrue();
        assertThat(Ruc.esValido("20601234567")).isFalse();  // dígito cambiado
        assertThat(Ruc.esValido("20123456789")).isFalse();
        assertThat(Ruc.esValido("30100066603")).isFalse();  // prefijo inexistente
        assertThat(Ruc.esValido("2010006660")).isFalse();
        assertThat(Ruc.esValido(null)).isFalse();
    }

    @Test void exigirValidoDistingueFormatoYDigito() {
        assertThatThrownBy(() -> Ruc.exigirValido("2010006660", "RUC_INVALIDO", "Empresa")).isInstanceOf(DomainException.class).hasMessageContaining("11 dígitos");
        assertThatThrownBy(() -> Ruc.exigirValido("20100066604", "RUC_INVALIDO", "Empresa")).hasMessageContaining("dígito verificador");
        Ruc.exigirValido("20100066603", "RUC_INVALIDO", "Empresa");
    }
}
