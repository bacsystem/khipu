package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #611: la retención de las sesiones vencidas es configurable, pero un valor fuera de 1..90 aborta el arranque. */
class AppConfigRetencionDeSesionesTest {
    @Test void dentroDelRangoSeUsaTalCual() {
        assertThat(AppConfig.retencionDeSesiones(7)).isEqualTo(Duration.ofDays(7));
        assertThat(AppConfig.retencionDeSesiones(1)).isEqualTo(Duration.ofDays(1));
        assertThat(AppConfig.retencionDeSesiones(90)).isEqualTo(Duration.ofDays(90));
    }

    @Test void fueraDelRangoAbortaElArranque() {
        for (int dias : new int[]{0, -1, 91, 365})
            assertThatThrownBy(() -> AppConfig.retencionDeSesiones(dias)).as("%d", dias)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("SESION_RETENCION_DIAS");
    }
}
