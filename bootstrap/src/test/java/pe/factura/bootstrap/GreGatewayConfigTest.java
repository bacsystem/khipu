package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.Entorno;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #39: cómo se construye la pasarela de la GRE al arrancar (H1, H5, H6). */
class GreGatewayConfigTest {
    static final String API = "https://api-cpe.sunat.gob.pe", TOKEN = "https://api-seguridad.sunat.gob.pe";

    @Test void porDefectoElEnvioEstaApagado() {
        assertThat(AppConfig.greGateway(false, TOKEN, "", API, "", 30, Clock.systemUTC()).disponible(Entorno.PRODUCCION)).isFalse();
        assertThat(AppConfig.greGateway(true, TOKEN, "", API, "", 30, Clock.systemUTC()).disponible(Entorno.PRODUCCION)).isTrue();
    }

    @Test void unaBetaQueApuntaASunatRealNoDejaArrancar() {
        assertThatThrownBy(() -> AppConfig.greGateway(true, TOKEN, "https://gre-test.ejemplo.pe/token", API, API, 30, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SUNAT_API_GRE_BETA_URL");
        assertThatThrownBy(() -> AppConfig.greGateway(true, TOKEN, TOKEN, API, "https://gre-test.ejemplo.pe", 30, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SUNAT_API_GRE_SEGURIDAD_BETA_URL");
        assertThatThrownBy(() -> AppConfig.greGateway(true, TOKEN, "", API, "https://gre-test.ejemplo.pe", 30, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SUNAT_API_GRE_SEGURIDAD_BETA_URL");
        assertThatThrownBy(() -> AppConfig.greGateway(false, TOKEN, "", API, "", 0, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
