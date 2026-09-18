package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Operaciones gratuitas (catálogo 07: 11–16, 21, 31–37 → tributo 9996): reglas 2640, 3110, 3111, 3224, 3234, 3276, 3302 y 54. */
class GratuitasTest {
    private static Item item(String precio, String cant, TipoAfectacionIgv af) {
        return new Item("P", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), af);
    }

    @Test void bonificacionGravadaInformaIgvPeroNoSeCobra() {
        // 5 unidades a valor referencial 10.00 (sin IGV): base 50.00, IGV 9.00 informativo, nada a pagar.
        ItemCalculado ic = ItemCalculado.de(item("10.00", "5", TipoAfectacionIgv.GRAVADO_BONIFICACION));
        assertThat(ic.gratuita()).isTrue();
        assertThat(ic.tributo()).isEqualTo(Tributo.GRA);
        assertThat(ic.tipoPrecio()).isEqualTo("02");
        assertThat(ic.valorUnitario()).isEqualByComparingTo("0");                    // 2640
        assertThat(ic.precioVentaUnitario()).isEqualByComparingTo("10.0000000000");  // valor referencial (3224)
        assertThat(ic.valorVenta()).isEqualByComparingTo("50.00");
        assertThat(ic.igv()).isEqualByComparingTo("9.00");                            // 3111: ≠ 0 en 11–16
        assertThat(ic.porcentajeIgv()).isEqualByComparingTo("18.00");                 // 2993
        assertThat(ic.precioVenta()).isEqualByComparingTo("0.00");
    }

    @Test void gratuitaInafectaNoLlevaIgv() {
        ItemCalculado ic = ItemCalculado.de(item("30.00", "1", TipoAfectacionIgv.INAFECTO_RETIRO_MUESTRAS_MEDICAS));
        assertThat(ic.igv()).isEqualByComparingTo("0.00");                            // 3110
        assertThat(ic.porcentajeIgv()).isEqualByComparingTo("0.00");
        assertThat(ic.tributo()).isEqualTo(Tributo.GRA);
    }

    @Test void totalesExcluyenLasGratuitasDelImporteAPagar() {
        Totales t = Totales.calcular(List.of(
                item("118.00", "1", TipoAfectacionIgv.GRAVADO),
                item("10.00", "5", TipoAfectacionIgv.GRAVADO_BONIFICACION),
                item("30.00", "1", TipoAfectacionIgv.EXONERADO_GRATUITO)));
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.gratuito()).isEqualByComparingTo("80.00");        // 50 + 30 (3276)
        assertThat(t.igv()).isEqualByComparingTo("18.00");             // solo tributo 1000
        assertThat(t.igvGratuitas()).isEqualByComparingTo("9.00");     // 3302
        assertThat(t.totalValorVenta()).isEqualByComparingTo("100.00"); // regla 54: sin 9996
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("118.00");
        assertThat(t.total()).isEqualByComparingTo("118.00");
        assertThat(t.tieneGratuitas()).isTrue();
        assertThat(t.subtotales()).extracting(Totales.SubtotalTributo::tributo).containsExactly(Tributo.IGV, Tributo.GRA);
        assertThat(t.subtotales().get(1).base()).isEqualByComparingTo("80.00");
        assertThat(t.subtotales().get(1).impuesto()).isEqualByComparingTo("9.00");
    }

    @Test void facturaSoloGratuitaTieneTotalCero() {
        Totales t = Totales.calcular(List.of(item("10.00", "2", TipoAfectacionIgv.GRAVADO_RETIRO_PUBLICIDAD)));
        assertThat(t.total()).isEqualByComparingTo("0.00");
        assertThat(t.gratuito()).isEqualByComparingTo("20.00");
        assertThat(t.igvGratuitas()).isEqualByComparingTo("3.60");
    }

    @Test void descuentoGlobal03NoSeAplicaSobreGratuitas() {
        Totales t = Totales.calcular(List.of(item("118.00", "1", TipoAfectacionIgv.GRAVADO), item("10.00", "1", TipoAfectacionIgv.GRAVADO_BONIFICACION)),
                Descuento.porcentaje(BigDecimal.TEN, false));
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("100.00");
        assertThat(t.total()).isEqualByComparingTo("108.00");
    }

    @Test void todasLasAfectacionesDelCatalogoMapeanASuTributo() {
        for (String c : new String[]{"11", "12", "13", "14", "15", "16", "21", "31", "32", "33", "34", "35", "36", "37"})
            assertThat(TipoAfectacionIgv.porCodigo(c).tributo()).as(c).isEqualTo(Tributo.GRA);
        assertThat(TipoAfectacionIgv.porCodigo("10").tributo()).isEqualTo(Tributo.IGV);
        assertThat(TipoAfectacionIgv.porCodigo("20").tributo()).isEqualTo(Tributo.EXO);
        assertThat(TipoAfectacionIgv.porCodigo("30").tributo()).isEqualTo(Tributo.INA);
        assertThat(TipoAfectacionIgv.porCodigo("21").gravado()).isFalse();
        assertThat(TipoAfectacionIgv.porCodigo("16").gravado()).isTrue();
    }
}
