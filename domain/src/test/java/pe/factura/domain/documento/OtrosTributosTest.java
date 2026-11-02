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
 * #317: otros tributos (9999) por línea y placa del vehículo (propiedad 7000, catálogo 55). Factura2_0: 3223 (combinaciones), 3109 (tasa × base), 3102 (formato de la
 * tasa), 3297/3299 (sumatorias), 3279 (el total de precio de venta suma «otros tributos» pero la base del IGV no los incluye) y 3064 (el valor de la placa).
 */
class OtrosTributosTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor CLIENTE = new Receptor("6", "20131312955", "SUNAT", "AV. GARCILASO DE LA VEGA 1472, LIMA");

    static Item item(String precio, TipoAfectacionIgv afectacion, Isc isc, OtrosTributos otros, String placa) {
        return new Item("P", "Combustible", "GLL", BigDecimal.ONE, new BigDecimal(precio), afectacion, null, isc, false, List.of(), null, null, null, null, otros, placa);
    }

    static OtrosTributos otros(String tasa) { return new OtrosTributos(new BigDecimal(tasa)); }

    @Test void lasOtrasTasasSumanAlTotalSinEntrarEnLaBaseDelIgv() {
        // Precio 118 con IGV → valor 100; otros tributos 3.5 % × 100 = 3.50; el IGV sigue siendo 18 % de 100.
        ItemCalculado ic = ItemCalculado.de(item("118.00", TipoAfectacionIgv.GRAVADO, null, otros("3.5"), null));
        assertThat(ic.valorVenta()).isEqualByComparingTo("100.00");
        assertThat(ic.otrosTributos()).isEqualByComparingTo("3.50");
        assertThat(ic.otrosTributosPorcentaje()).isEqualByComparingTo("3.5");
        assertThat(ic.igv()).isEqualByComparingTo("18.00");
        assertThat(ic.baseIgv()).isEqualByComparingTo("100.00");               // los otros tributos no forman parte de la base del IGV
        assertThat(ic.totalTributos()).isEqualByComparingTo("21.50");           // 3292: 1000 + 9999
        assertThat(ic.precioVenta()).isEqualByComparingTo("121.50");

        Totales t = Totales.calcular(List.of(item("118.00", TipoAfectacionIgv.GRAVADO, null, otros("3.5"), null)));
        assertThat(t.otrosTributos()).isEqualByComparingTo("3.50");
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("100.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("121.50");       // 3279
        assertThat(t.total()).isEqualByComparingTo("121.50");
        assertThat(t.subtotales()).filteredOn(s -> s.tributo() == Tributo.OTROS).singleElement().satisfies(s -> {
            assertThat(s.base()).isEqualByComparingTo("100.00");                  // 3297
            assertThat(s.impuesto()).isEqualByComparingTo("3.50");                // 3299
        });
    }

    @Test void conIscElOtroTributoVaSobreElValorDeVenta() {
        // ISC 10 % al valor + otros 2 %: valor 100, ISC 10, IGV (100 + 10) × 18 % = 19.80, otros 2 % × 100 = 2.00.
        Isc isc = new Isc("01", new BigDecimal("10"), null);
        Totales t = Totales.calcular(List.of(item("129.80", TipoAfectacionIgv.GRAVADO, isc, otros("2"), null)));
        assertThat(t.isc()).isEqualByComparingTo("10.00");
        assertThat(t.igv()).isEqualByComparingTo("19.80");
        assertThat(t.otrosTributos()).isEqualByComparingTo("2.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("131.80");
    }

    @Test void unaLineaExoneradaPuedeLlevarOtrosTributos() {
        Totales t = Totales.calcular(List.of(item("100.00", TipoAfectacionIgv.EXONERADO, null, otros("5"), null)));
        assertThat(t.exonerado()).isEqualByComparingTo("100.00");
        assertThat(t.otrosTributos()).isEqualByComparingTo("5.00");
        assertThat(t.total()).isEqualByComparingTo("105.00");
    }

    @ParameterizedTest(name = "la tasa {0} no es válida (3102)")
    @ValueSource(strings = {"0", "-1", "1000", "1.123456"})
    void laTasaEsPositivaDeTresEnterosYCincoDecimales(String tasa) {
        assertThatThrownBy(() -> otros(tasa)).isInstanceOf(DomainException.class).hasMessageContaining("3102").extracting("codigo").isEqualTo("OTROS_TRIBUTOS_INVALIDO");
    }

    @Test void unaGratuitaYUnaExportacionNoLlevanOtrosTributos() {
        assertThatThrownBy(() -> ItemCalculado.de(item("10.00", TipoAfectacionIgv.GRAVADO_BONIFICACION, null, otros("2"), null)))
                .hasMessageContaining("3223").extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
        assertThatThrownBy(() -> ItemCalculado.de(item("10.00", TipoAfectacionIgv.EXPORTACION, null, otros("2"), null)))
                .hasMessageContaining("3107").extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
    }

    @Test void laPlacaVaDeUnoAOchoCaracteres() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", CLIENTE,
                List.of(item("118.00", TipoAfectacionIgv.GRAVADO, null, null, " ABC-123 "))).crear(CLOCK);
        assertThat(c.items().get(0).placa()).isEqualTo("ABC-123");
        for (String mala : new String[]{"", "   ", "ABCDEFGHI", "AB\nC"})
            assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", CLIENTE,
                    List.of(item("118.00", TipoAfectacionIgv.GRAVADO, null, null, mala))).crear(CLOCK))
                    .as("placa «" + mala + "»").isInstanceOf(DomainException.class).hasMessageContaining("3064").extracting("codigo").isEqualTo("ITEM_INVALIDO");
    }

    @Test void noSeCombinanConUnDescuentoGlobalQueMueveLaBase() {
        List<Item> items = List.of(item("118.00", TipoAfectacionIgv.GRAVADO, null, otros("3.5"), null));
        assertThatThrownBy(() -> Totales.calcular(items, Descuento.porcentaje(new BigDecimal("5"), true)))
                .isInstanceOf(DomainException.class).hasMessageContaining("9999").extracting("codigo").isEqualTo("OTROS_TRIBUTOS_INVALIDO");
        // Un descuento global que no toca la base (03) sí se combina: no mueve el IGV.
        Totales t = Totales.calcular(items, Descuento.porcentaje(new BigDecimal("5"), false));
        assertThat(t.otrosTributos()).isEqualByComparingTo("3.50");
    }

    @Test void unaNotaConservaLosOtrosTributosDeLaLineaConDescuento() {
        Item conDescuento = new Item("P", "Combustible", "GLL", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.monto(new BigDecimal("10.00"), true),
                null, false, List.of(), null, null, null, null, otros("2"), "ABC-123");
        Totales factura = Totales.calcular(List.of(conDescuento));
        Totales nota = factura.paraNota();
        assertThat(nota.otrosTributos()).isEqualByComparingTo(factura.otrosTributos());
        assertThat(nota.total()).isEqualByComparingTo(factura.total());
        assertThat(nota.items().get(0).otrosTributos()).isEqualByComparingTo("1.80");   // 2 % × 90.00 de valor de venta
        assertThat(nota.items().get(0).item().placa()).isEqualTo("ABC-123");
    }

    @Test void unaFacturaConOtrosTributosSeCreaConSuTotal() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", CLIENTE,
                List.of(item("118.00", TipoAfectacionIgv.GRAVADO, null, otros("3.5"), "ABC-123"))).crear(CLOCK);
        assertThat(c.totales().total()).isEqualByComparingTo("121.50");
        assertThat(c.items().get(0).otrosTributos().tasa()).isEqualByComparingTo("3.5");
    }
}
