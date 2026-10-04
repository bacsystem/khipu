package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

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
    /**
     * El consumo de un plan (#192) cuenta solo los comprobantes que SUNAT aceptó. Cada estado se clasifica a propósito: un estado nuevo no puede colarse en el
     * cobro (ni quedarse fuera) sin que este test obligue a decidir. {@code ANULADO} no cuenta: es lo que el plan comercial llama «las bajas no cuentan».
     */
    @Test void soloLoAceptadoCuentaParaElConsumo() {
        assertThat(EnumSet.allOf(EstadoDocumento.class).stream().filter(EstadoDocumento::cuentaParaElConsumo).toList()).containsExactlyInAnyOrder(ACEPTADO, ACEPTADO_CON_OBS);
        for (EstadoDocumento e : new EstadoDocumento[]{RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO, RECHAZADO, ANULADO, FUERA_DE_PLAZO})
            assertThat(e.cuentaParaElConsumo()).as(e.name()).isFalse();
    }

    @Test void enviable() {
        assertThat(FIRMADO.esEnviable()).isTrue();
        assertThat(ERROR_ENVIO.esEnviable()).isTrue();
        assertThat(ACEPTADO.esEnviable()).isFalse();
    }
}
