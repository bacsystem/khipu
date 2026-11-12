package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #611, #634: la retención de las sesiones y de los enlaces de acceso vencidos es configurable, pero un valor fuera de 1..90 aborta el arranque. */
class AppConfigRetencionDeAccesosTest {
    @Test void dentroDelRangoSeUsaTalCual() {
        assertThat(AppConfig.retencionDeAccesos(7)).isEqualTo(Duration.ofDays(7));
        assertThat(AppConfig.retencionDeAccesos(1)).isEqualTo(Duration.ofDays(1));
        assertThat(AppConfig.retencionDeAccesos(90)).isEqualTo(Duration.ofDays(90));
    }

    @Test void fueraDelRangoAbortaElArranque() {
        for (int dias : new int[]{0, -1, 91, 365})
            assertThatThrownBy(() -> AppConfig.retencionDeAccesos(dias)).as("%d", dias)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACCESO_RETENCION_DIAS");
    }
}
