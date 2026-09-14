package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppConfigSecretosTest {
    private static AppProperties con(String masterKey, String pepper) {
        return new AppProperties("multi", masterKey, pepper, "plataforma", "jwt-secret", "http://localhost:3000",
                null, null, null, new AppProperties.Mail(false, "no-responder@factura.pe"), "America/Lima");
    }

    @Test void secretosPresentesPasan() {
        assertThatCode(() -> AppConfig.exigirSecretosDePlataforma(con("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "pepper-real")))
                .doesNotThrowAnyException();
    }

    @Test void secretoAusenteOVacioAborta() {
        assertThatThrownBy(() -> AppConfig.exigirSecretosDePlataforma(con(null, "pepper"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AppConfig.exigirSecretosDePlataforma(con("clave", "  "))).isInstanceOf(IllegalStateException.class);
    }

    @Test void placeholderHistoricoAbortaSinDistinguirMayusculas() {
        for (String placeholder : new String[]{"cambiar-en-produccion", "CAMBIAR-EN-PRODUCCION", " Cambiar-En-Produccion "}) {
            assertThatThrownBy(() -> AppConfig.exigirSecretosDePlataforma(con(placeholder, "pepper"))).as("master " + placeholder)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("cambiar-en-produccion");
            assertThatThrownBy(() -> AppConfig.exigirSecretosDePlataforma(con("clave", placeholder))).as("pepper " + placeholder)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
