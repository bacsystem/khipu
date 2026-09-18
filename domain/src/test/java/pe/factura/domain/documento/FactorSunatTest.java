package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** MultiplierFactorNumeric de cargos y descuentos: solo se informa cuando base × factor reproduce el monto ±1 (reglas 3290, 3307). */
class FactorSunatTest {

    @Test void factorSoloCuandoReproduceElMonto() {
        assertThat(FactorSunat.de(new BigDecimal("20.00"), new BigDecimal("200.00"))).hasValue(new BigDecimal("0.10000"));
        // 1 700 000 × 0.00059 = 1 003.00: se desvía 3 del monto → sin factor
        assertThat(FactorSunat.de(new BigDecimal("1000.00"), new BigDecimal("1700000.00"))).isEmpty();
        assertThat(FactorSunat.de(new BigDecimal("1500.00"), new BigDecimal("2500000.00"))).hasValue(new BigDecimal("0.00060"));
    }

    @Test void sinFactorConBaseCeroOMontoCero() {
        assertThat(FactorSunat.de(new BigDecimal("5.00"), BigDecimal.ZERO)).isEmpty();
        assertThat(FactorSunat.de(BigDecimal.ZERO, new BigDecimal("100.00"))).isEmpty();
    }
}
