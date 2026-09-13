package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.documento.EstadoDocumento.*;

class EstadoDocumentoTest {
    @Test void transicionesValidas() {
        assertThat(RECIBIDO.puedeTransitarA(FIRMADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(FIRMADO.puedeTransitarA(ERROR_ENVIO)).isTrue();
        assertThat(ERROR_ENVIO.puedeTransitarA(ENVIADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(ACEPTADO_CON_OBS)).isTrue();
        assertThat(ENVIADO.puedeTransitarA(RECHAZADO)).isTrue();
        assertThat(ACEPTADO.puedeTransitarA(ANULADO)).isTrue();
    }
    @Test void transicionesInvalidas() {
        assertThat(RECIBIDO.puedeTransitarA(ACEPTADO)).isFalse();
        assertThat(RECHAZADO.puedeTransitarA(ENVIADO)).isFalse();
        assertThat(ACEPTADO.puedeTransitarA(ENVIADO)).isFalse();
    }
    @Test void enviable() {
        assertThat(FIRMADO.esEnviable()).isTrue();
        assertThat(ERROR_ENVIO.esEnviable()).isTrue();
        assertThat(ACEPTADO.esEnviable()).isFalse();
    }
}
