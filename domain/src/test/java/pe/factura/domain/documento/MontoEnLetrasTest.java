package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MontoEnLetrasTest {
    @Test void casos() {
        assertThat(MontoEnLetras.de(new BigDecimal("118.00"), "PEN")).isEqualTo("CIENTO DIECIOCHO CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1.50"), "PEN")).isEqualTo("UNO CON 50/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("21.00"), "USD")).isEqualTo("VEINTIUNO CON 00/100 DOLARES AMERICANOS");
        assertThat(MontoEnLetras.de(new BigDecimal("100.00"), "PEN")).isEqualTo("CIEN CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1000.00"), "PEN")).isEqualTo("MIL CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("2500.75"), "PEN")).isEqualTo("DOS MIL QUINIENTOS CON 75/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("1000000.00"), "PEN")).isEqualTo("UN MILLON CON 00/100 SOLES");
        assertThat(MontoEnLetras.de(new BigDecimal("3016.00"), "PEN")).isEqualTo("TRES MIL DIECISEIS CON 00/100 SOLES");
    }
}
