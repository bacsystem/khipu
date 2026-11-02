package pe.factura.domain.cuenta;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AceptacionDeTerminosTest {
    static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");

    @Test void guardaUsuarioVersionYFecha() {
        UUID usuario = UUID.randomUUID();
        AceptacionDeTerminos a = new AceptacionDeTerminos(usuario, " 2026-10-20 ", AHORA);
        assertThat(a.usuarioId()).isEqualTo(usuario);
        assertThat(a.version()).as("sin los espacios").isEqualTo("2026-10-20");
        assertThat(a.aceptadaEn()).isEqualTo(AHORA);
    }

    @Test void laVersionEsCortaYSinEspacios() {
        assertThatCode(() -> AceptacionDeTerminos.validarVersion("v2.1_borrador-3")).doesNotThrowAnyException();
        for (String mala : new String[]{null, "", "  ", "2026 10 20", "a".repeat(41), "v1;DROP"})
            assertThatThrownBy(() -> AceptacionDeTerminos.validarVersion(mala)).as("«%s»", mala)
                    .extracting("codigo").isEqualTo("VERSION_TERMINOS_INVALIDA");
    }

    @Test void exigeUsuarioYFecha() {
        assertThatThrownBy(() -> new AceptacionDeTerminos(null, "2026-10-20", AHORA)).extracting("codigo").isEqualTo("USUARIO_REQUERIDO");
        assertThatThrownBy(() -> new AceptacionDeTerminos(UUID.randomUUID(), "2026-10-20", null)).extracting("codigo").isEqualTo("FECHA_REQUERIDA");
    }
}
