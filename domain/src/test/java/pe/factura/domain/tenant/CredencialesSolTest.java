package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredencialesSolTest {

    /**
     * El espacio pegado al copiar y pegar es el caso real: viajaba dentro del UsernameToken, SUNAT rechazaba la
     * autenticación en todos los envíos, y la pantalla Empresa seguía mostrando «CONFIGURADAS».
     */
    @Test void elUsuarioSeNormalizaYLaClaveNo() {
        CredencialesSol sol = new CredencialesSol(" MODDATOS ", " moddatos ");
        assertThat(sol.usuario()).isEqualTo("MODDATOS");
        assertThat(sol.usernameToken("20100066603")).isEqualTo("20100066603MODDATOS");
        // La clave se guarda tal cual: un espacio al borde puede ser parte de la clave real.
        assertThat(sol.clave()).isEqualTo(" moddatos ");
    }

    @Test void elUsuarioNoAdmiteEspaciosInternos() {
        assertThatThrownBy(() -> new CredencialesSol("MOD DATOS", "x"))
                .isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("CREDENCIALES_SOL_INVALIDAS");
        assertThatThrownBy(() -> new CredencialesSol("MOD\tDATOS", "x")).hasMessageContaining("espacios");
    }

    @Test void usuarioYClaveSonObligatorios() {
        assertThatThrownBy(() -> new CredencialesSol("   ", "x")).extracting("codigo").isEqualTo("CREDENCIALES_SOL_INVALIDAS");
        assertThatThrownBy(() -> new CredencialesSol("MODDATOS", "  ")).extracting("codigo").isEqualTo("CREDENCIALES_SOL_INVALIDAS");
        assertThatThrownBy(() -> new CredencialesSol(null, "x")).extracting("codigo").isEqualTo("CREDENCIALES_SOL_INVALIDAS");
        assertThatThrownBy(() -> new CredencialesSol("MODDATOS", null)).extracting("codigo").isEqualTo("CREDENCIALES_SOL_INVALIDAS");
    }
}
