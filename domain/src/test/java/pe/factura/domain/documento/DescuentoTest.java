package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Descuentos del catálogo 53 y su efecto en bases, IGV y totales (reglas 38, 46/47, 51, 53/54 de la hoja Factura2_0). */
class DescuentoTest {
    private static Item gravado(String precio, String cant, Descuento d) {
        return new Item("P", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO, d);
    }

    @Test void descuentoDeLineaQueAfectaLaBase_codigo00() {
        // 2 × 118.00 (con IGV) = base bruta 200.00; 10 % → 20.00; valor de venta 180.00; IGV 32.40
        ItemCalculado ic = ItemCalculado.de(gravado("118.00", "2", Descuento.porcentaje(new BigDecimal("10"), true)));
        assertThat(ic.baseBruta()).isEqualByComparingTo("200.00");
        assertThat(ic.descuento()).isEqualByComparingTo("20.00");
        assertThat(ic.descuentoFactor()).hasValue(new BigDecimal("0.10000"));
        assertThat(ic.valorVenta()).isEqualByComparingTo("180.00");
        assertThat(ic.igv()).isEqualByComparingTo("32.40");
        assertThat(ic.precioVenta()).isEqualByComparingTo("212.40");
        assertThat(ic.precioVentaUnitario()).isEqualByComparingTo("106.2000000000");   // regla 33: (180 + 32.40) / 2
        assertThat(ic.valorUnitario()).isEqualByComparingTo("100.0000000000");        // sin descuento (regla 38)
        assertThat(ic.item().descuento().codigoSunat(false)).isEqualTo("00");
    }

    @Test void descuentoDeLineaQueNoAfectaLaBase_codigo01() {
        // Descuento financiero: el IGV se calcula sobre la base completa; solo baja lo que se paga.
        ItemCalculado ic = ItemCalculado.de(gravado("118.00", "1", Descuento.monto(new BigDecimal("10.00"), false)));
        assertThat(ic.valorVenta()).isEqualByComparingTo("100.00");
        assertThat(ic.igv()).isEqualByComparingTo("18.00");
        assertThat(ic.descuentoNoAfectaBase()).isEqualByComparingTo("10.00");
        assertThat(ic.precioVenta()).isEqualByComparingTo("108.00");
        assertThat(ic.item().descuento().codigoSunat(false)).isEqualTo("01");
        Totales t = Totales.calcular(List.of(ic.item()));
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("118.00");
        assertThat(t.totalDescuentos()).isEqualByComparingTo("10.00");
        assertThat(t.total()).isEqualByComparingTo("108.00");
    }

    @Test void descuentoGlobalQueAfectaLaBase_codigo02() {
        // Dos líneas gravadas (base 100 + 200 = 300) y una exonerada (50); 10 % global → 30.00 sobre la base gravada.
        Totales t = Totales.calcular(List.of(gravado("118.00", "1", null), gravado("236.00", "1", null),
                new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                Descuento.porcentaje(new BigDecimal("10"), true));
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("300.00");
        assertThat(t.descuentoGlobal().monto()).isEqualByComparingTo("30.00");
        assertThat(t.descuentoGlobal().codigo()).isEqualTo("02");
        assertThat(t.gravado()).isEqualByComparingTo("270.00");
        assertThat(t.igv()).isEqualByComparingTo("48.60");          // 270 × 18 % (regla 47), no la suma de IGV de línea (54.00)
        assertThat(t.exonerado()).isEqualByComparingTo("50.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("320.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("368.60");
        assertThat(t.totalDescuentos()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("368.60");
    }

    @Test void descuentoGlobalQueNoAfectaLaBase_codigo03() {
        Totales t = Totales.calcular(List.of(gravado("118.00", "1", null)), Descuento.monto(new BigDecimal("18.00"), false));
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
        assertThat(t.descuentoGlobal().codigo()).isEqualTo("03");
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("100.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("118.00");
        assertThat(t.totalDescuentos()).isEqualByComparingTo("18.00");
        assertThat(t.total()).isEqualByComparingTo("100.00");
    }

    @Test void descuentoGlobal02SinLineasGravadasSeRechaza() {
        assertThatThrownBy(() -> Totales.calcular(List.of(new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                Descuento.porcentaje(new BigDecimal("5"), true)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DESCUENTO_INVALIDO");
    }

    @Test void descuentoInvalido() {
        assertThatThrownBy(() -> Descuento.porcentaje(new BigDecimal("100"), true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Descuento.monto(new BigDecimal("0.00"), true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Descuento.monto(new BigDecimal("1.005"), true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ItemCalculado.de(gravado("118.00", "1", Descuento.monto(new BigDecimal("100.00"), true))))
                .hasMessageContaining("menor que la base");
    }

    /** Reglas 3290/3307: el factor de 5 decimales solo se informa si base × factor reproduce el monto (±1). */
    @Test void factorSoloCuandoReproduceElMonto() {
        assertThat(Descuento.factor(new BigDecimal("20.00"), new BigDecimal("200.00"))).hasValue(new BigDecimal("0.10000"));
        // 1 700 000 × 0.00059 = 1 003.00: se desvía 3 del monto → sin factor
        assertThat(Descuento.factor(new BigDecimal("1000.00"), new BigDecimal("1700000.00"))).isEmpty();
        assertThat(Descuento.factor(new BigDecimal("1500.00"), new BigDecimal("2500000.00"))).hasValue(new BigDecimal("0.00060"));
        ItemCalculado grande = ItemCalculado.de(gravado("2006000.00", "1", Descuento.monto(new BigDecimal("1000.00"), true)));
        assertThat(grande.descuentoFactor()).isEmpty();
        assertThat(grande.valorVenta()).isEqualByComparingTo("1699000.00");
        assertThat(ItemCalculado.de(gravado("118.00", "2", Descuento.porcentaje(new BigDecimal("10"), true))).descuentoFactor()).hasValue(new BigDecimal("0.10000"));
    }

    @Test void sinDescuentosLosTotalesNoCambian() {
        Totales t = Totales.calcular(List.of(gravado("118.00", "1", null)));
        assertThat(t.descuentoGlobal()).isNull();
        assertThat(t.totalValorVenta()).isEqualByComparingTo("100.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("118.00");
        assertThat(t.totalDescuentos()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("118.00");
        assertThat(t.items().get(0).precioVentaUnitario()).isEqualByComparingTo("118.0000000000");
    }
}
