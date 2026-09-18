package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TotalesTest {
    private Item item(String precio, String cant, TipoAfectacionIgv af) {
        return new Item("P1", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), af);
    }

    @Test void gravadoDesglosaIgvDelPrecio() {
        Totales t = Totales.calcular(List.of(item("118.00", "1", TipoAfectacionIgv.GRAVADO)));
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
        assertThat(t.total()).isEqualByComparingTo("118.00");
        ItemCalculado i = t.items().get(0);
        assertThat(i.valorUnitario()).isEqualByComparingTo("100.0000000000");
        assertThat(i.precioVenta()).isEqualByComparingTo("118.00");
    }

    @Test void mezclaDeAfectaciones() {
        Totales t = Totales.calcular(List.of(
                item("118.00", "2", TipoAfectacionIgv.GRAVADO),
                item("50.00", "2", TipoAfectacionIgv.EXONERADO),
                item("100.00", "1", TipoAfectacionIgv.INAFECTO)));
        assertThat(t.gravado()).isEqualByComparingTo("200.00");
        assertThat(t.exonerado()).isEqualByComparingTo("100.00");
        assertThat(t.inafecto()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("36.00");
        assertThat(t.total()).isEqualByComparingTo("436.00");
    }

    /** Los subtotales son la fuente de gravado/exonerado/inafecto/igv: solo aparecen los que tienen base, en orden de catálogo, y suman lo mismo. */
    @Test void subtotalesPorAfectacionSonLaFuenteDeLosTotales() {
        Totales t = Totales.calcular(List.of(
                item("100.00", "1", TipoAfectacionIgv.INAFECTO),
                item("118.00", "2", TipoAfectacionIgv.GRAVADO)));
        assertThat(t.subtotales()).extracting(Totales.SubtotalTributo::tributo)
                .containsExactly(Tributo.IGV, Tributo.INA);   // orden de catálogo, sin exonerado
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo(t.gravado());
        assertThat(t.subtotales().get(0).impuesto()).isEqualByComparingTo(t.igv());
        assertThat(t.subtotales().get(1).base()).isEqualByComparingTo(t.inafecto());
        assertThat(t.subtotales().get(1).impuesto()).isEqualByComparingTo("0.00");
        assertThat(t.exonerado()).isEqualByComparingTo("0.00");
        assertThat(t.subtotales().stream().map(Totales.SubtotalTributo::base).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(t.gravado().add(t.exonerado()).add(t.inafecto()));
    }

    @Test void redondeoADosDecimales() {
        Totales t = Totales.calcular(List.of(item("10.00", "3", TipoAfectacionIgv.GRAVADO)));
        assertThat(t.gravado()).isEqualByComparingTo("25.42");
        assertThat(t.igv()).isEqualByComparingTo("4.58");
        assertThat(t.total()).isEqualByComparingTo("30.00");
    }
}
