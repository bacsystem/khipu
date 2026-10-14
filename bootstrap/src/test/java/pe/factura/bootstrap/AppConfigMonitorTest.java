package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #362 B14: el monitor del backoffice alerta de un resumen diario o una baja con ticket que SUNAT no responde pasadas N horas. N se configura
 * (`app.monitor.resumen-sin-respuesta-horas`, 6 por defecto) pero un valor fuera de rango aborta el arranque: 0 alertaría de todo lo que está en curso, y una semana
 * ya no sirve como alerta.
 */
class AppConfigMonitorTest {
    @Test void dentroDelRangoSeUsaTalCual() {
        assertThat(AppConfig.umbralDeResumenesSinRespuesta(new AppProperties.Monitor(6))).isEqualTo(Duration.ofHours(6));
        assertThat(AppConfig.umbralDeResumenesSinRespuesta(new AppProperties.Monitor(1))).isEqualTo(Duration.ofHours(1));
        assertThat(AppConfig.umbralDeResumenesSinRespuesta(new AppProperties.Monitor(168))).isEqualTo(Duration.ofHours(168));
    }

    @Test void sinConfiguracionSonSeisHoras() {
        assertThat(AppConfig.umbralDeResumenesSinRespuesta(null)).isEqualTo(Duration.ofHours(6));
    }

    @Test void fueraDelRangoAbortaElArranque() {
        for (int horas : new int[]{0, -1, 169, 1000})
            assertThatThrownBy(() -> AppConfig.umbralDeResumenesSinRespuesta(new AppProperties.Monitor(horas))).as("%d", horas)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("MONITOR_RESUMEN_SIN_RESPUESTA_HORAS");
    }
}
