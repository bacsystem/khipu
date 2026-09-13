package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CdrTest {
    @Test void codigoCeroEsAceptadoYNoEsRechazo() {
        Cdr cdr = new Cdr("0", "ok", List.of());
        assertThat(cdr.esAceptado()).isTrue();
        assertThat(cdr.esRechazo()).isFalse();
    }

    @Test void codigoEnRangoEsRechazo() {
        Cdr cdr = new Cdr("2324", "El comprobante fue registrado previamente con otros datos", List.of());
        assertThat(cdr.esRechazo()).isTrue();
    }

    @Test void codigoNoNumericoLanzaDomainException() {
        Cdr cdr = new Cdr("ABC", "x", List.of());
        assertThatThrownBy(cdr::esRechazo)
                .isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("CDR_INVALIDO");
    }
}
