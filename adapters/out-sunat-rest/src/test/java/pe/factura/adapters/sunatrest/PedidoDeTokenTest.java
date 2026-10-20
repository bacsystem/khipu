package pe.factura.adapters.sunatrest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El pedido de token sirve a los dos grants de SUNAT (#318: client_credentials para la validez; password para la GRE y el SIRE) y nunca muestra secretos. */
class PedidoDeTokenTest {
    static final String URL = "https://api-seguridad.sunat.gob.pe/v1/clientessol/abc/oauth2/token/";

    @Test void elGrantPasswordLlevaElUsuarioYLaClaveSolComoPideLaGre() {
        PedidoDeToken p = PedidoDeToken.password(URL, "https://api-cpe.sunat.gob.pe", "abc", "s3cr&to", "20100066603MODDATOS", "clave sol");

        assertThat(p.formulario()).isEqualTo("grant_type=password&scope=https%3A%2F%2Fapi-cpe.sunat.gob.pe&client_id=abc&client_secret=s3cr%26to"
                + "&username=20100066603MODDATOS&password=clave+sol");
    }

    @Test void elGrantClientCredentialsNoLlevaUsuario() {
        PedidoDeToken p = PedidoDeToken.clientCredentials(URL, "scope", "abc", "s");
        assertThat(p.formulario()).doesNotContain("username").doesNotContain("password");
    }

    @Test void elToStringYLaClaveDeCacheNoMuestranSecretos() {
        PedidoDeToken p = PedidoDeToken.password(URL, "scope", "abc", "secreto-api", "20100066603MODDATOS", "clave-sol");
        assertThat(p.toString()).contains("abc").contains("20100066603MODDATOS").doesNotContain("secreto-api").doesNotContain("clave-sol");
        assertThat(p.claveDeCache()).doesNotContain("secreto-api").doesNotContain("clave-sol");
    }

    @Test void laClaveDeCacheCambiaSiCambiaElSecretoOLaClaveSol() {
        PedidoDeToken a = PedidoDeToken.password(URL, "scope", "abc", "s1", "u", "c1");
        assertThat(a.claveDeCache()).isEqualTo(PedidoDeToken.password(URL, "scope", "abc", "s1", "u", "c1").claveDeCache())
                .isNotEqualTo(PedidoDeToken.password(URL, "scope", "abc", "s2", "u", "c1").claveDeCache())
                .isNotEqualTo(PedidoDeToken.password(URL, "scope", "abc", "s1", "u", "c2").claveDeCache());
    }

    @Test void sinSecretosTachaElSecretoYLaClaveSol() {
        PedidoDeToken p = PedidoDeToken.password(URL, "scope", "abc", "secreto-api", "u", "clave-sol");
        assertThat(p.sinSecretos("mal: secreto-api y clave-sol")).isEqualTo("mal: *** y ***");
    }
}
