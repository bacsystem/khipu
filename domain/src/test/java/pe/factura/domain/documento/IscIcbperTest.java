package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ISC (tributo 2000, base del IGV incluye el ISC — regla 204) e ICBPER (7152, monto fijo por bolsa — reglas 3236–3238). */
class IscIcbperTest {
    private static Item item(String precio, String cant, Isc isc, boolean icbper) {
        return new Item("P", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO, null, isc, icbper);
    }

    @Test void iscAlValor_precioIncluyeIscEIgv() {
        // Precio 159.30 = valor 100 × 1.35 (ISC 35 %) × 1.18 (IGV)
        ItemCalculado ic = ItemCalculado.de(item("159.30", "1", new Isc("01", new BigDecimal("35"), null), false));
        assertThat(ic.valorUnitario()).isEqualByComparingTo("100.0000000000");
        assertThat(ic.valorVenta()).isEqualByComparingTo("100.00");
        assertThat(ic.isc()).isEqualByComparingTo("35.00");
        assertThat(ic.iscPorcentaje()).isEqualByComparingTo("35");
        assertThat(ic.baseIgv()).isEqualByComparingTo("135.00");     // regla 204
        assertThat(ic.igv()).isEqualByComparingTo("24.30");
        assertThat(ic.totalTributos()).isEqualByComparingTo("59.30"); // regla 3292
        assertThat(ic.precioVenta()).isEqualByComparingTo("159.30");
    }

    @Test void iscMontoFijo_derivaLaTasaParaLaRegla3108() {
        // 10 unidades, ISC fijo 2.25/u, valor 5.00/u: precio = (5 + 2.25) × 1.18 = 8.555
        ItemCalculado ic = ItemCalculado.de(item("8.555", "10", new Isc("02", null, new BigDecimal("2.25")), false));
        assertThat(ic.valorVenta()).isEqualByComparingTo("50.00");
        assertThat(ic.isc()).isEqualByComparingTo("22.50");
        assertThat(ic.iscPorcentaje()).isEqualByComparingTo("45");   // 22.50 / 50.00 → Percent × base = monto
        assertThat(ic.igv()).isEqualByComparingTo("13.05");           // (50 + 22.50) × 18 %
    }

    @Test void icbperPorBolsaSegunElAnio() {
        assertThat(Icbper.tasaVigente(LocalDate.of(2019, 6, 1))).isEqualByComparingTo("0.10");
        assertThat(Icbper.tasaVigente(LocalDate.of(2021, 6, 1))).isEqualByComparingTo("0.30");
        assertThat(Icbper.tasaVigente(LocalDate.of(2026, 9, 17))).isEqualByComparingTo("0.50");
        // 3 bolsas a 0.10 c/u de valor + 0.50 ICBPER: precio 0.618 = 0.10 × 1.18 + 0.50
        ItemCalculado ic = ItemCalculado.de(item("0.618", "3", null, true), new BigDecimal("0.50"));
        assertThat(ic.icbperUnitario()).isEqualByComparingTo("0.50");
        assertThat(ic.icbper()).isEqualByComparingTo("1.50");
        assertThat(ic.valorVenta()).isEqualByComparingTo("0.30");
        assertThat(ic.igv()).isEqualByComparingTo("0.05");
        assertThat(ic.precioVenta()).isEqualByComparingTo("1.85");
        assertThat(ic.totalTributos()).isEqualByComparingTo("1.55");
    }

    @Test void totalesConIscEIcbper() {
        Totales t = Totales.calcular(List.of(
                item("159.30", "1", new Isc("01", new BigDecimal("35"), null), false),
                item("0.618", "3", null, true),
                item("118.00", "1", null, false)), null, new BigDecimal("0.50"));
        assertThat(t.subtotales()).extracting(Totales.SubtotalTributo::tributo).containsExactly(Tributo.IGV, Tributo.ISC, Tributo.ICBPER);
        assertThat(t.gravado()).isEqualByComparingTo("200.30");          // 100 + 0.30 + 100
        assertThat(t.isc()).isEqualByComparingTo("35.00");
        assertThat(t.icbper()).isEqualByComparingTo("1.50");
        assertThat(t.igv()).isEqualByComparingTo("42.35");               // 24.30 + 0.05 + 18.00
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo("235.30");   // base del IGV incluye el ISC
        assertThat(t.subtotales().get(1).base()).isEqualByComparingTo("100.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("200.30");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("279.15"); // 200.30 + 35 + 1.50 + 42.35 (regla 55)
        assertThat(t.total()).isEqualByComparingTo("279.15");
    }

    @Test void iscInvalido() {
        assertThatThrownBy(() -> new Isc("04", BigDecimal.TEN, null)).isInstanceOf(DomainException.class).hasMessageContaining("2041");
        assertThatThrownBy(() -> new Isc("01", null, null)).hasMessageContaining("3104");
        assertThatThrownBy(() -> new Isc("02", BigDecimal.TEN, BigDecimal.ONE)).hasMessageContaining("no lleva tasa");
    }
}
