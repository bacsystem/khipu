package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import pe.factura.application.service.VigilarOperacionService.Umbrales;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #341: los umbrales del vigilante salen de `app.alertas.*`; sin configurar valen los de siempre y un valor fuera de rango aborta el arranque. */
class AppConfigAlertasTest {
    static final Duration RESUMENES = Duration.ofHours(6);

    static AppProperties.Alertas con(int repetir, int minutos, int cantidad, int respaldo, int ventana) {
        return new AppProperties.Alertas("", "", repetir, minutos, cantidad, respaldo, ventana);
    }

    @Test void sinConfiguracionSeUsanLosValoresPorDefectoYElRespaldoQuedaApagado() {
        assertThat(AppConfig.umbralesDeAlertas(null, RESUMENES))
                .isEqualTo(new Umbrales(Duration.ofHours(24), Duration.ofMinutes(30), 5, RESUMENES, Duration.ZERO, Duration.ofHours(24)));
    }

    @Test void losValoresConfiguradosSeUsanTalCual() {
        assertThat(AppConfig.umbralesDeAlertas(con(12, 15, 3, 26, 48), RESUMENES))
                .isEqualTo(new Umbrales(Duration.ofHours(12), Duration.ofMinutes(15), 3, RESUMENES, Duration.ofHours(26), Duration.ofHours(48)));
    }

    @Test void fueraDelRangoAbortaElArranqueNombrandoLaVariable() {
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(0, 30, 5, 0, 24), RESUMENES)).hasMessageContaining("ALERTAS_REPETIR_HORAS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(169, 30, 5, 0, 24), RESUMENES)).hasMessageContaining("ALERTAS_REPETIR_HORAS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 0, 5, 0, 24), RESUMENES)).hasMessageContaining("ALERTAS_ERROR_ENVIO_MINUTOS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 1441, 5, 0, 24), RESUMENES)).hasMessageContaining("ALERTAS_ERROR_ENVIO_MINUTOS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 30, 0, 0, 24), RESUMENES)).hasMessageContaining("ALERTAS_ERROR_ENVIO_CANTIDAD");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 30, 5, -1, 24), RESUMENES)).hasMessageContaining("ALERTAS_RESPALDO_HORAS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 30, 5, 721, 24), RESUMENES)).hasMessageContaining("ALERTAS_RESPALDO_HORAS");
        assertThatThrownBy(() -> AppConfig.umbralesDeAlertas(con(24, 30, 5, 0, 0), RESUMENES)).hasMessageContaining("ALERTAS_FUERA_DE_PLAZO_HORAS");
    }
}
