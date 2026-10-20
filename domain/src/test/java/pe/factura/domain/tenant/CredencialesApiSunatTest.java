package pe.factura.domain.tenant;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Credenciales de la API SUNAT (#318): el client_id se normaliza, el secreto no se toca y nunca se imprime. */
class CredencialesApiSunatTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";

    @Test void elClientIdSeNormalizaYElSecretoNo() {
        CredencialesApiSunat c = new CredencialesApiSunat("  " + ID + " ", " HhWwZVo8ZaWm4BZ7n/xEzw== ");
        assertThat(c.clientId()).isEqualTo(ID);
        assertThat(c.clientSecret()).isEqualTo(" HhWwZVo8ZaWm4BZ7n/xEzw== ");
    }

    @Test void ambosSonObligatorios() {
        for (String[] par : new String[][]{{null, "x"}, {ID, null}, {"  ", "x"}, {ID, "   "}})
            assertThatThrownBy(() -> new CredencialesApiSunat(par[0], par[1])).isInstanceOf(DomainException.class)
                    .extracting("codigo").isEqualTo("CREDENCIALES_API_SUNAT_INVALIDAS");
    }

    /** El client_id va en la ruta de la URL del token: una barra o un «?» la cambiarían de recurso. */
    @Test void elClientIdNoAdmiteNadaQueCambieLaUrlDelToken() {
        for (String malo : new String[]{"abc def", "abc/../otro", "abc?x=1", "abc#x", "abc%2F", "..", ".", "-abc", ".abc"})
            assertThatThrownBy(() -> new CredencialesApiSunat(malo, "s")).as(malo).extracting("codigo").isEqualTo("CREDENCIALES_API_SUNAT_INVALIDAS");
    }

    @Test void unValorDemasiadoLargoEsUnPegadoEquivocado() {
        assertThatThrownBy(() -> new CredencialesApiSunat("a".repeat(201), "s")).hasMessageContaining("200");
        assertThatThrownBy(() -> new CredencialesApiSunat(ID, "s".repeat(201))).hasMessageContaining("200");
    }

    @Test void elToStringNoMuestraElSecreto() {
        CredencialesApiSunat c = new CredencialesApiSunat(ID, "secreto-que-no-sale");
        assertThat(c.toString()).contains(ID).doesNotContain("secreto-que-no-sale");
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, null, null).conCredencialesApiSunat(c);
        assertThat(t.toString()).as("ni dentro de la empresa").doesNotContain("secreto-que-no-sale");
    }

    @Test void laEmpresaLasConservaAlCambiarOtrosDatosYSeBorranConNull() {
        CredencialesApiSunat c = new CredencialesApiSunat(ID, "s");
        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, null, null).conCredencialesApiSunat(c);
        assertThat(t.conCredencialesSol(new CredencialesSol("MODDATOS", "moddatos")).apiSunat()).isEqualTo(c);
        assertThat(t.conDatosFiscales(null, null, "Comercial", true).apiSunat()).isEqualTo(c);
        assertThat(t.conPersonalizacionPdf(null).apiSunat()).isEqualTo(c);
        assertThat(t.conDomicilio(null).apiSunat()).isEqualTo(c);
        assertThat(t.conIdentidadImpresa("20100066603", "X", null, null).apiSunat()).isEqualTo(c);
        assertThat(t.conCredencialesApiSunat(null).apiSunat()).isNull();
    }
}
