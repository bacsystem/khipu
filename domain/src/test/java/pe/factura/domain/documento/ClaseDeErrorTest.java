package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.documento.EstadoDocumento.*;

/**
 * La cola de errores del backoffice (#196) reparte los comprobantes con problema en tres clases. Un error de formato no es un estado propio: es un rechazo por fault de SUNAT
 * con código 1000–1999 (error del contenido o del emisor, que no cambia por reintentar).
 */
class ClaseDeErrorTest {
    @Test void unErrorDeEnvioYUnoFueraDePlazoSonDeSuClase() {
        assertThat(ClaseDeError.de(ERROR_ENVIO, null)).contains(ClaseDeError.ERROR_DE_ENVIO);
        assertThat(ClaseDeError.de(FUERA_DE_PLAZO, null)).contains(ClaseDeError.FUERA_DE_PLAZO);
    }

    @Test void unRechazoPorFaultDeFormatoEsUnErrorDeFormato() {
        for (String codigo : new String[]{"1000", "1033", "1500", "1999"}) assertThat(ClaseDeError.de(RECHAZADO, codigo)).as(codigo).contains(ClaseDeError.ERROR_DE_FORMATO);
    }

    @Test void unRechazoDeOtroCodigoNoEstaEnLaCola() {
        for (String codigo : new String[]{"0999", "999", "2000", "2324", "3999", "0", "", "abcd", "10000", "100", "1 99"})
            assertThat(ClaseDeError.de(RECHAZADO, codigo)).as(codigo).isEmpty();
        assertThat(ClaseDeError.de(RECHAZADO, null)).isEmpty();
    }

    @Test void elCodigoSoloCuentaEnUnRechazo() {
        for (EstadoDocumento e : EstadoDocumento.values()) {
            if (e == RECHAZADO || e == ERROR_ENVIO || e == FUERA_DE_PLAZO) continue;
            assertThat(ClaseDeError.de(e, "1033")).as(e.name()).isEqualTo(Optional.empty());
        }
        assertThat(ClaseDeError.de(ERROR_ENVIO, "1033")).as("el código de un CDR viejo no cambia la clase de un error de envío").contains(ClaseDeError.ERROR_DE_ENVIO);
    }

    @Test void soloEstosTresEstadosTienenClase() {
        for (EstadoDocumento e : EstadoDocumento.values())
            assertThat(ClaseDeError.de(e, null)).as(e.name()).isEqualTo(e == ERROR_ENVIO ? Optional.of(ClaseDeError.ERROR_DE_ENVIO) : e == FUERA_DE_PLAZO ? Optional.of(ClaseDeError.FUERA_DE_PLAZO) : Optional.empty());
    }

    @Test void soloUnErrorDeEnvioSePuedeReintentarODescartar() {
        assertThat(ClaseDeError.ERROR_DE_ENVIO.accionable()).isTrue();
        assertThat(ClaseDeError.ERROR_DE_FORMATO.accionable()).isFalse();
        assertThat(ClaseDeError.FUERA_DE_PLAZO.accionable()).isFalse();
    }
}
