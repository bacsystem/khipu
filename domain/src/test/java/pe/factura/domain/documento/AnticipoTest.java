package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Anticipos regularizados en la factura (reglas 65–66 de la hoja Factura2_0): el valor sin IGV reduce la base del tributo
 * (3277, 3291) pero no el total valor/precio de venta (3278, 3279); el importe pagado con IGV es el PrepaidAmount y se resta
 * del importe a pagar (2509, 3280).
 */
class AnticipoTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC);

    private static Item item(String precio, TipoAfectacionIgv af) {
        return new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal(precio), af);
    }

    private static Anticipo gravado(String monto) { return new Anticipo("F001", 10, new BigDecimal(monto), null, null); }

    @Test void anticipoGravadoReduceLaBaseDelIgvYElImporteAPagar() {
        // Operación completa 1000 + IGV 180; anticipo de 300 (pagó 354) → IGV sobre 700 = 126; a pagar 826
        Totales t = Totales.calcular(List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)), null, List.of(gravado("300.00")), Icbper.tasaVigente(LocalDate.of(2026, 1, 1)));
        assertThat(t.totalValorVenta()).isEqualByComparingTo("1000.00");    // bruto (regla 3278)
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("1180.00");   // bruto (regla 3279)
        assertThat(t.gravado()).isEqualByComparingTo("700.00");             // neto (regla 3277)
        assertThat(t.igv()).isEqualByComparingTo("126.00");                 // regla 3291
        assertThat(t.totalAnticipos()).isEqualByComparingTo("354.00");      // 300 + IGV
        assertThat(t.total()).isEqualByComparingTo("826.00");               // 1180 − 354 (regla 3280)
        assertThat(t.anticipos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo("04");
            assertThat(a.monto()).isEqualByComparingTo("300.00");
            assertThat(a.base()).isEqualByComparingTo("1000.00");
            assertThat(a.importePagado()).isEqualByComparingTo("354.00");
        });
        assertThat(t.subtotales()).singleElement().satisfies(st -> {
            assertThat(st.tributo()).isEqualTo(Tributo.IGV);
            assertThat(st.base()).isEqualByComparingTo("700.00");
            assertThat(st.impuesto()).isEqualByComparingTo("126.00");
        });
    }

    @Test void anticipoPorElTotalDejaBaseEImporteEnCero() {
        Totales t = Totales.calcular(List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)), null, List.of(gravado("1000.00")), Icbper.tasaVigente(LocalDate.of(2026, 1, 1)));
        assertThat(t.gravado()).isEqualByComparingTo("0.00");
        assertThat(t.igv()).isEqualByComparingTo("0.00");
        assertThat(t.totalAnticipos()).isEqualByComparingTo("1180.00");
        assertThat(t.total()).isEqualByComparingTo("0.00");
    }

    @Test void anticipoExoneradoEInafectoReducenSuPropiaBaseSinIgv() {
        Totales t = Totales.calcular(List.of(item("500.00", TipoAfectacionIgv.EXONERADO), item("200.00", TipoAfectacionIgv.INAFECTO)), null,
                List.of(new Anticipo("F001", 1, new BigDecimal("100.00"), Anticipo.Afectacion.EXONERADO, null),
                        new Anticipo("F001", 2, new BigDecimal("50.00"), Anticipo.Afectacion.INAFECTO, LocalDate.of(2026, 9, 1))),
                Icbper.tasaVigente(LocalDate.of(2026, 1, 1)));
        assertThat(t.exonerado()).isEqualByComparingTo("400.00");
        assertThat(t.inafecto()).isEqualByComparingTo("150.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("700.00");
        assertThat(t.totalAnticipos()).isEqualByComparingTo("150.00");   // sin IGV (05/06)
        assertThat(t.total()).isEqualByComparingTo("550.00");
        assertThat(t.anticipos()).extracting(Totales.AnticipoCalculado::codigo).containsExactly("05", "06");
    }

    @Test void variosAnticiposGravadosSeAcumulanYNoPuedenSuperarLaBase() {
        Totales t = Totales.calcular(List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)), null,
                List.of(gravado("600.00"), new Anticipo("F001", 11, new BigDecimal("400.00"), null, null)), Icbper.tasaVigente(LocalDate.of(2026, 1, 1)));
        assertThat(t.gravado()).isEqualByComparingTo("0.00");
        assertThat(t.anticipos()).extracting(Totales.AnticipoCalculado::base).allSatisfy(b -> assertThat(b).isEqualByComparingTo("1000.00"));

        assertThatThrownBy(() -> Totales.calcular(List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)), null,
                List.of(gravado("600.00"), new Anticipo("F001", 11, new BigDecimal("400.01"), null, null)), Icbper.tasaVigente(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("ANTICIPO_INVALIDO");
        // Sin líneas de esa afectación no hay base que descontar
        assertThatThrownBy(() -> Totales.calcular(List.of(item("500.00", TipoAfectacionIgv.EXONERADO)), null, List.of(gravado("10.00")), Icbper.tasaVigente(LocalDate.of(2026, 1, 1))))
                .hasMessageContaining("supera el valor de venta gravado pendiente");
    }

    @Test void conDescuentoGlobal02ElAnticipoSeRestaDeLaBaseYaDescontada() {
        // Base 1000 − 10 % (02) = 900; anticipo 300 → base 600, IGV 108; precio de venta bruto 900 + 162 = 1062; a pagar 1062 − 354 = 708
        Totales t = Totales.calcular(List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)), Descuento.porcentaje(new BigDecimal("10"), true),
                List.of(gravado("300.00")), Icbper.tasaVigente(LocalDate.of(2026, 1, 1)));
        assertThat(t.gravado()).isEqualByComparingTo("600.00");
        assertThat(t.igv()).isEqualByComparingTo("108.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("900.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("1062.00");
        assertThat(t.total()).isEqualByComparingTo("708.00");
        assertThat(t.anticipos().get(0).base()).isEqualByComparingTo("900.00");
    }

    @Test void validaFormatoYRechazaLaMismaFacturaDosVeces() {
        assertThatThrownBy(() -> new Anticipo("B001", 1, BigDecimal.TEN, null, null)).hasMessageContaining("2521");
        assertThatThrownBy(() -> new Anticipo("F001", 0, BigDecimal.TEN, null, null)).hasMessageContaining("2521");
        assertThatThrownBy(() -> new Anticipo("F001", 1, BigDecimal.ZERO, null, null)).hasMessageContaining("2503");
        assertThatThrownBy(() -> new Anticipo("F001", 1, new BigDecimal("1.005"), null, null)).hasMessageContaining("2503");
        assertThat(gravado("100.00").afectacion()).isEqualTo(Anticipo.Afectacion.GRAVADO);
        assertThat(gravado("100.00").comprobante()).isEqualTo("F001-10");
        assertThat(gravado("100.00").importePagado()).isEqualByComparingTo("118.00");
        assertThat(new Anticipo("F001", 1, new BigDecimal("100.00"), Anticipo.Afectacion.EXONERADO, null).importePagado()).isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 15), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null), List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, null, null, null, List.of(gravado("100.00"), gravado("200.00")), CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("3215");
    }

    @Test void laFormaDePagoAlCreditoSeValidaContraElSaldoTrasAnticipos() {
        // 1180 − 354 = 826 pendiente: las cuotas deben sumar el saldo, no el precio de venta bruto
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 15), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null), List.of(item("1180.00", TipoAfectacionIgv.GRAVADO)),
                FormaPago.credito(new BigDecimal("826.00"), List.of(new FormaPago.Cuota(new BigDecimal("826.00"), LocalDate.of(2026, 10, 15)))),
                null, null, null, null, List.of(gravado("300.00")), CLOCK);
        assertThat(c.totales().total()).isEqualByComparingTo("826.00");
        assertThat(c.anticipos()).hasSize(1);
    }
}
