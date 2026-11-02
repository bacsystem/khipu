package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #295: Boleta2_0 admite anticipos (3211–3220, 3287): el comprobante que recibió el anticipo es una factura (02, serie F) o una boleta (03, serie B), y la boleta final
 * lleva el mismo trío que la factura (documento referenciado, PrepaidPayment y descuento global 04/05/06 con PrepaidAmount).
 */
class AnticipoEnBoletaTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate HOY = LocalDate.of(2026, 9, 13);
    static final Receptor DNI = new Receptor("1", "12345678", "JUAN PEREZ", null);
    static final Item OBRA = new Item("OBRA", "Obra completa", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO);

    static Comprobante.FacturaBuilder boleta(Anticipo... anticipos) {
        return Comprobante.boleta(UUID.randomUUID(), "B001", HOY, "PEN", "0101", DNI, List.of(OBRA)).anticipos(List.of(anticipos));
    }

    @Test void elTipoDelComprobanteDelAnticipoSaleDeLaSerie() {
        assertThat(new Anticipo("F001", 10, BigDecimal.TEN, null, null).tipoComprobante()).isEqualTo("02");
        assertThat(new Anticipo("B001", 10, BigDecimal.TEN, null, null).tipoComprobante()).isEqualTo("03");
        assertThat(new Anticipo("B001", 10, BigDecimal.TEN, null, null).esDeBoleta()).isTrue();
        assertThat(new Anticipo("F001", 10, BigDecimal.TEN, null, null).esDeBoleta()).isFalse();
    }

    @ParameterizedTest(name = "la serie {0} no es de un anticipo")
    @ValueSource(strings = {"X001", "FA", "B0001", "b001", "E001"})
    void laSerieDebeSerDeFacturaODeBoleta(String serie) {
        assertThatThrownBy(() -> new Anticipo(serie, 1, BigDecimal.TEN, null, null)).isInstanceOf(DomainException.class).hasMessageContaining("2521");
    }

    @Test void unaBoletaDescuentaUnAnticipoDeFactura() {
        Comprobante c = boleta(new Anticipo("F001", 7, new BigDecimal("100.00"), null, null)).crear(CLOCK);
        assertThat(c.totales().totalAnticipos()).isEqualByComparingTo("118.00");
        assertThat(c.totales().gravado()).isEqualByComparingTo("900.00");     // 3277: la base baja con el anticipo
        assertThat(c.totales().total()).isEqualByComparingTo("1062.00");     // 3280: 1180 − 118
        assertThat(c.anticipos()).singleElement().extracting(Anticipo::comprobante).isEqualTo("F001-7");
    }

    @Test void unaBoletaDescuentaUnAnticipoDeBoleta() {
        Comprobante c = boleta(new Anticipo("B001", 3, new BigDecimal("200.00"), null, LocalDate.of(2026, 9, 1))).crear(CLOCK);
        assertThat(c.totales().totalAnticipos()).isEqualByComparingTo("236.00");
        assertThat(c.totales().total()).isEqualByComparingTo("944.00");
        assertThat(c.anticipos()).singleElement().extracting(Anticipo::tipoComprobante).isEqualTo("03");
    }

    @Test void elMismoAnticipoDosVecesEnLaBoletaSeRechaza() {
        assertThatThrownBy(() -> boleta(new Anticipo("B001", 3, BigDecimal.TEN, null, null), new Anticipo("B001", 3, BigDecimal.ONE, null, null)).crear(CLOCK))
                .hasMessageContaining("3215").extracting("codigo").isEqualTo("ANTICIPO_INVALIDO");
    }
}
