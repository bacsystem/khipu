package pe.factura.adapters.sunatrest;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.Entorno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** H6 (#318): una URL mal configurada impide arrancar con un mensaje claro, en vez de un 500 sin fallback en la primera consulta. */
class UrlPorEntornoTest {
    @Test void quitaLaBarraFinalYEligePorEntorno() {
        UrlPorEntorno u = new UrlPorEntorno("https://beta.sunat.gob.pe/", " https://api.sunat.gob.pe// ");
        assertThat(u.para(Entorno.BETA)).isEqualTo("https://beta.sunat.gob.pe");
        assertThat(u.para(Entorno.PRODUCCION)).isEqualTo("https://api.sunat.gob.pe");
        assertThat(new UrlPorEntorno("http://localhost:18089/api", "http://localhost:18089/api").para(Entorno.BETA)).isEqualTo("http://localhost:18089/api");
    }

    @Test void sinEsquemaSinServidorOVaciaNoArranca() {
        for (String mala : new String[]{"api.sunat.gob.pe", "ftp://api.sunat.gob.pe", "https://", "https:///v1", "https://api sunat", "   "})
            assertThatThrownBy(() -> new UrlPorEntorno(mala, "https://api.sunat.gob.pe")).as(mala).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("URL de la API de SUNAT (beta)");
        assertThatThrownBy(() -> new UrlPorEntorno("https://api.sunat.gob.pe", null)).hasMessageContaining("producción");
    }
}
