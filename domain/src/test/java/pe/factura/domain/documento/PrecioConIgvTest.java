package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H9: el emisor manda el precio con IGV, y el cliente paga exactamente eso. Antes el IGV se calculaba sobre la base redondeada (base × 18 %): con S/ 10.00 la base es
 * 8.47 y el IGV 1.52, y el total salía 9.99. Ahora, en una línea gravada sin ISC, descuentos ni cargos, el IGV es lo que falta para llegar al precio: 1.53. La
 * diferencia con base × 18 % es de milésimos (menos de un céntimo), muy dentro de la tolerancia de ±1 de SUNAT (observación 4290).
 */
class PrecioConIgvTest {
    static Item gravado(String cantidad, String precio) {
        return new Item("P", "Prod", "NIU", new BigDecimal(cantidad), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO);
    }

    @Test void diezSolesConIgvSePaganDiezSoles() {
        ItemCalculado c = ItemCalculado.de(gravado("1", "10.00"));

        assertThat(c.valorVenta()).isEqualByComparingTo("8.47");
        assertThat(c.igv()).isEqualByComparingTo("1.53");
        assertThat(c.precioVenta()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest(name = "{0} × S/ {1} con IGV se paga S/ {2}")
    @CsvSource({ "1, 10.00, 10.00", "3, 10.00, 30.00", "1, 118.00, 118.00", "7, 0.99, 6.93", "2.5, 4.20, 10.50", "1, 1.00, 1.00", "12, 3.33, 39.96" })
    void elTotalEsElPrecioPorLaCantidadYElIgvQuedaDentroDeLaToleranciaDeSunat(String cantidad, String precio, String total) {
        ItemCalculado c = ItemCalculado.de(gravado(cantidad, precio));

        assertThat(c.precioVenta()).isEqualByComparingTo(total);
        assertThat(c.valorVenta().add(c.igv())).isEqualByComparingTo(total);
        BigDecimal igvExacto = c.valorVenta().multiply(new BigDecimal("0.18"));
        assertThat(c.igv().subtract(igvExacto).abs()).as("SUNAT tolera ±1 (4290); aquí es menos de un céntimo").isLessThan(new BigDecimal("0.01"));
    }

    @Test void conDescuentoElIgvSigueSiendoSobreLaBase() {
        // Con un descuento no hay un «precio con IGV» que reproducir: el IGV se calcula sobre la base que queda, como siempre.
        Item conDescuento = new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO,
                Descuento.monto(new BigDecimal("1.00"), true));

        ItemCalculado c = ItemCalculado.de(conDescuento);

        assertThat(c.igv()).isEqualByComparingTo(c.valorVenta().multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP));
    }

    /*
     * Regla 3291 (antes 4290): la suma del IGV de las líneas no puede alejarse más de 1.00 de la suma de bases × tasa. Cada línea a precio exacto se aleja
     * milésimos, siempre hacia el mismo lado con el mismo precio: S/ 10.00 se aleja 0.0054 (1.53 contra 1.5246). Con muchas líneas eso se acumula, así que
     * pasado 0.50 el comprobante entero vuelve a base × tasa.
     */
    static java.util.List<Item> lineasDeDiezSoles(int n) {
        return java.util.Collections.nCopies(n, gravado("1", "10.00"));
    }

    @Test void conPocasLineasElComprobanteConservaElPrecioExacto() {
        Totales t = Totales.calcular(lineasDeDiezSoles(90));   // se aleja 90 × 0.0054 = 0.486

        assertThat(t.igv()).isEqualByComparingTo("137.70");
        assertThat(t.total()).isEqualByComparingTo("900.00");
    }

    @Test void siLaSumaSeAlejaMasDeMedioSolVuelveABasePorTasa() {
        Totales t = Totales.calcular(lineasDeDiezSoles(100));  // a precio exacto se alejaría 0.54

        assertThat(t.items()).allSatisfy(i -> assertThat(i.igv()).isEqualByComparingTo("1.52"));
        assertThat(t.igv()).isEqualByComparingTo("152.00");
        assertThat(t.total()).isEqualByComparingTo("999.00");
        assertThat(t.igv().subtract(t.gravado().multiply(new BigDecimal("0.18"))).abs()).isLessThanOrEqualTo(new BigDecimal("0.50"));
    }
}
