package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #638: el corte por inactividad se apaga con 0 o va de 15 a 480 minutos; un valor fuera de rango aborta el arranque en vez de dejar una sesión eterna. */
class AppConfigSesionInactividadTest {
    @Test void dentroDelRangoSeUsaTalCual() {
        assertThat(AppConfig.inactividadDeSesion(30)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AppConfig.inactividadDeSesion(15)).isEqualTo(Duration.ofMinutes(15));
        assertThat(AppConfig.inactividadDeSesion(480)).isEqualTo(Duration.ofMinutes(480));
    }

    @Test void ceroDesactivaElCorte() {
        assertThat(AppConfig.inactividadDeSesion(0)).isEqualTo(Duration.ZERO);
    }

    @Test void fueraDelRangoAbortaElArranqueYElMensajeDiceElRango() {
        for (int minutos : new int[]{14, 1, 481, 1440, -1})
            assertThatThrownBy(() -> AppConfig.inactividadDeSesion(minutos)).as("%d", minutos)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("SESION_INACTIVIDAD_MINUTOS debe ser 0 (desactivado) o estar entre 15 y 480 (es " + minutos + ")");
    }
}
