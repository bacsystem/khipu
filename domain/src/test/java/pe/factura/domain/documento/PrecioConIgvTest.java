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
}
