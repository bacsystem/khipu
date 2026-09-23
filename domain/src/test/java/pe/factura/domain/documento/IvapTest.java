package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IVAP (#67): afectación 17 del catálogo 07, tributo 1016 del catálogo 05, 4 % en lugar del IGV (Ley 28211). SUNAT exige que el
 * comprobante entero sea IVAP: el total precio de venta se calcula sin IGV (campo 55, variante IVAP de la 3279) y la línea no
 * combina con ISC ni ICBPER (2650/3223).
 */
class IvapTest {
    private static Item arroz(String precio, String cant) {
        return new Item("ARZ", "Arroz pilado", "KGM", new BigDecimal(cant), new BigDecimal(precio), TipoAfectacionIgv.IVAP);
    }

    @Test void lineaIvapTributaCuatroPorCiento() {
        // Precio 3.12/kg con IVAP = 3.00 valor + 4 %
        ItemCalculado ic = ItemCalculado.de(arroz("3.12", "100"));
        assertThat(ic.tributo()).isEqualTo(Tributo.IVAP);
        assertThat(ic.valorUnitario()).isEqualByComparingTo("3.0000000000");
        assertThat(ic.valorVenta()).isEqualByComparingTo("300.00");
        assertThat(ic.igv()).isEqualByComparingTo("12.00");
        assertThat(ic.porcentajeIgv()).isEqualByComparingTo("4.00");
        assertThat(ic.precioVenta()).isEqualByComparingTo("312.00");
    }

    /** 3111: base > 0.06 con IVAP 0.00 (importes 0.07–0.12 con impuesto incluido). Con 0.13 el 4 % ya redondea a 0.01. */
    @Test void unaLineaIvapCuyoImpuestoRedondeaACeroSeRechaza() {
        assertThatThrownBy(() -> ItemCalculado.de(arroz("0.10", "1"))).isInstanceOf(DomainException.class).hasMessageContaining("3111");
        assertThatThrownBy(() -> ItemCalculado.de(arroz("0.05", "2"))).isInstanceOf(DomainException.class).hasMessageContaining("3111");
        assertThat(ItemCalculado.de(arroz("0.13", "1")).igv()).isEqualByComparingTo("0.01");
        assertThat(ItemCalculado.de(arroz("0.06", "1")).igv()).isEqualByComparingTo("0.00"); // base 0.0577 ≤ 0.06: SUNAT no lo exige
    }

    @Test void laTasaEspecialDelPadronNoAfectaAlIvap() {
        ItemCalculado ic = ItemCalculado.de(arroz("3.12", "100"), BigDecimal.ZERO, new BigDecimal("10.50"));
        assertThat(ic.igv()).isEqualByComparingTo("12.00");
        assertThat(ic.porcentajeIgv()).isEqualByComparingTo("4.00");
    }

    @Test void totalesConIvap() {
        Totales t = Totales.calcular(List.of(arroz("3.12", "100"), arroz("5.20", "10")));
        assertThat(t.subtotales()).extracting(Totales.SubtotalTributo::tributo).containsExactly(Tributo.IVAP);
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo("350.00");
        assertThat(t.subtotales().get(0).impuesto()).isEqualByComparingTo("14.00");
        assertThat(t.gravado()).isEqualByComparingTo("350.00");     // 3278: la base IVAP suma al valor de venta
        assertThat(t.igv()).isEqualByComparingTo("0.00");
        assertThat(t.ivap()).isEqualByComparingTo("14.00");
        assertThat(t.tieneIvap()).isTrue();
        assertThat(t.totalValorVenta()).isEqualByComparingTo("350.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("364.00"); // campo 55 (IVAP): valor + IVAP, sin IGV
        assertThat(t.total()).isEqualByComparingTo("364.00");
    }

    @Test void descuentoGlobalRecalculaElIvapSobreLaBaseNeta() {
        Totales t = Totales.calcular(List.of(arroz("3.12", "100")), Descuento.porcentaje(BigDecimal.TEN, true), BigDecimal.ZERO);
        assertThat(t.gravado()).isEqualByComparingTo("270.00");
        assertThat(t.ivap()).isEqualByComparingTo("10.80");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("280.80");
    }

    @Test void anticipoGravadoDescuentaDeLaBaseIvap() {
        // Anticipo de 100 de valor de venta (catálogo 53: 04, sin impuesto): se pagó 104 con IVAP, no 118 con IGV
        Anticipo a = new Anticipo("F001", 5L, new BigDecimal("100.00"), Anticipo.Afectacion.GRAVADO, null);
        Totales t = Totales.calcular(List.of(arroz("3.12", "100")), null, List.of(), List.of(a), BigDecimal.ZERO, null, TasaIgv.GENERAL);
        assertThat(t.anticipos()).hasSize(1);
        assertThat(t.anticipos().get(0).base()).isEqualByComparingTo("300.00");
        assertThat(t.anticipos().get(0).importePagado()).isEqualByComparingTo("104.00");
        assertThat(t.gravado()).isEqualByComparingTo("200.00");
        assertThat(t.ivap()).isEqualByComparingTo("8.00");
        assertThat(t.totalAnticipos()).isEqualByComparingTo("104.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("312.00");          // bruto (3279): el anticipo no lo reduce
        assertThat(t.total()).isEqualByComparingTo("208.00");                     // 312 − 104 pagado
    }

    @Test void noSeMezclaConOtrasAfectaciones() {
        assertThatThrownBy(() -> Totales.calcular(List.of(arroz("3.12", "100"),
                new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
        assertThatThrownBy(() -> Totales.calcular(List.of(arroz("3.12", "100"),
                new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))))
                .hasMessageContaining("IVAP");
        assertThatThrownBy(() -> Totales.calcular(List.of(arroz("3.12", "100"),
                new Item("P", "Bonif", "NIU", BigDecimal.ONE, new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION))))
                .hasMessageContaining("gratuitas");
    }

    @Test void noAdmiteIscNiIcbper_2650() {
        assertThatThrownBy(() -> ItemCalculado.de(new Item("A", "Arroz", "KGM", BigDecimal.ONE, new BigDecimal("3.12"), TipoAfectacionIgv.IVAP, null, new Isc("01", BigDecimal.TEN, null), false)))
                .isInstanceOf(DomainException.class).hasMessageContaining("2650");
        assertThatThrownBy(() -> ItemCalculado.de(new Item("A", "Bolsa", "NIU", BigDecimal.ONE, new BigDecimal("0.62"), TipoAfectacionIgv.IVAP, null, null, true)))
                .hasMessageContaining("2650");
    }

    @Test void catalogos() {
        assertThat(TipoAfectacionIgv.porCodigo("17")).isEqualTo(TipoAfectacionIgv.IVAP);
        assertThat(TipoAfectacionIgv.IVAP.gravado()).isTrue();
        assertThat(TipoAfectacionIgv.IVAP.gratuita()).isFalse();
        assertThat(Tributo.IVAP.codigo()).isEqualTo("1016");
        assertThat(Tributo.IVAP.nombre()).isEqualTo("IVAP");
        assertThat(Tributo.IVAP.tipoInternacional()).isEqualTo("VAT");
        assertThat(Tributo.IVAP.categoria()).isEqualTo("S");
        assertThat(TasaIgv.IVAP).isEqualByComparingTo("4.00");
    }
}
