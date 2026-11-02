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

    /**
     * #331: es observado si trae notas o si el propio ResponseCode es una observación (4000 en adelante, catálogo de códigos de retorno). En el borde: 3999 es
     * rechazo, 4000 ya es observación; un 0 sin notas, aceptado a secas.
     */
    @Test void esObservadoDesdeElCodigo4000OConNotas() {
        assertThat(new Cdr("4000", "El documento ya fue presentado anteriormente", List.of()).esObservado()).isTrue();
        assertThat(new Cdr("4443", "El comprobante fue informado fuera del plazo establecido", List.of()).esObservado()).isTrue();
        assertThat(new Cdr("3999", "rechazo", List.of("4443 - x")).esObservado()).as("un rechazo nunca es observado, aunque traiga notas").isFalse();
        assertThat(new Cdr("0", "ok", List.of()).esObservado()).isFalse();
        assertThat(new Cdr("0", "ok", List.of("4443 - El comprobante fue informado fuera del plazo establecido")).esObservado()).isTrue();
    }

    @Test void codigoNoNumericoLanzaDomainException() {
        Cdr cdr = new Cdr("ABC", "x", List.of());
        assertThatThrownBy(cdr::esRechazo)
                .isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("CDR_INVALIDO");
    }
}
