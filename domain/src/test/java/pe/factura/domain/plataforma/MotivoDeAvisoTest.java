package pe.factura.domain.plataforma;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Los avisos del backoffice a un cliente (#197): qué los motiva, a qué tipo pertenecen y cuánto hay que esperar para repetir el mismo. */
class MotivoDeAvisoTest {
    @Test void cadaMotivoPerteneceAUnTipo() {
        assertThat(MotivoDeAviso.CERTIFICADO_POR_VENCER.tipo()).isEqualTo(TipoDeAviso.CERTIFICADO);
        assertThat(MotivoDeAviso.CERTIFICADO_VENCIDO.tipo()).isEqualTo(TipoDeAviso.CERTIFICADO);
        assertThat(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS.tipo()).isEqualTo(TipoDeAviso.CREDENCIALES_SOL);
    }

    /** Un tipo tiene sus motivos: el certificado, dos (por vencer y vencido: pasar de uno a otro es un aviso nuevo); las credenciales, uno. */
    @Test void cadaTipoTieneSusMotivos() {
        assertThat(MotivoDeAviso.de(TipoDeAviso.CERTIFICADO)).containsExactly(MotivoDeAviso.CERTIFICADO_POR_VENCER, MotivoDeAviso.CERTIFICADO_VENCIDO);
        assertThat(MotivoDeAviso.de(TipoDeAviso.CREDENCIALES_SOL)).containsExactly(MotivoDeAviso.CREDENCIALES_SOL_INVALIDAS);
    }

    @Test void elMismoAvisoNoSeRepiteAntesDeUnaSemana() {
        assertThat(MotivoDeAviso.ENFRIAMIENTO).isEqualTo(Duration.ofDays(7));
        Instant aviso = Instant.parse("2026-10-01T15:00:00Z");

        assertThat(MotivoDeAviso.CERTIFICADO_POR_VENCER.avisarDesde(aviso)).isEqualTo(Instant.parse("2026-10-08T15:00:00Z"));
    }

    @Test void elMotivoSeLeeDeSuNombreYUnoDesconocidoNoExiste() {
        assertThat(MotivoDeAviso.valueOf("CERTIFICADO_VENCIDO")).isEqualTo(MotivoDeAviso.CERTIFICADO_VENCIDO);
        assertThat(TipoDeAviso.valueOf("CREDENCIALES_SOL")).isEqualTo(TipoDeAviso.CREDENCIALES_SOL);
    }
}
