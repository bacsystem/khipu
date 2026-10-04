package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #177: la sesión del administrador es corta y configurable, pero un valor fuera de rango aborta el arranque en vez de alargarla. */
class AppConfigSesionAdminTest {
    @Test void dentroDelRangoSeUsaTalCual() {
        assertThat(AppConfig.vidaSesionAdmin(30)).isEqualTo(Duration.ofMinutes(30));
        assertThat(AppConfig.vidaSesionAdmin(5)).isEqualTo(Duration.ofMinutes(5));
        assertThat(AppConfig.vidaSesionAdmin(60)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test void fueraDelRangoAbortaElArranque() {
        for (int minutos : new int[]{0, 4, 61, 720, -1})
            assertThatThrownBy(() -> AppConfig.vidaSesionAdmin(minutos)).as("%d", minutos)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_SESION_MINUTOS");
    }
}
