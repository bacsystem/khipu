package pe.factura.domain.documento;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cargos del catálogo 53 (47/48 por línea, 46/49/50 globales) y su efecto en bases, IGV y totales (reglas 38, 33, 3277, 3278, 3301, 3280). */
class CargoTest {
    private static Item gravado(String precio, String cant, Cargo... cargos) {
        return new Item("P", "Prod", "NIU", new BigDecimal(cant), new BigDecimal(precio), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(cargos));
    }

    @Test void cargoDeLineaQueAfectaLaBase_codigo47() {
        // 2 × 118.00 = base bruta 200.00; flete 10 % → 20.00 se suma al valor de venta y paga IGV.
        ItemCalculado ic = ItemCalculado.de(gravado("118.00", "2", Cargo.porcentaje("47", new BigDecimal("10"))));
        assertThat(ic.cargos()).hasSize(1);
        assertThat(ic.cargos().get(0).monto()).isEqualByComparingTo("20.00");
        assertThat(ic.cargos().get(0).base()).isEqualByComparingTo("200.00");
        assertThat(ic.cargos().get(0).factor()).hasValue(new BigDecimal("0.10000"));
        assertThat(ic.valorVenta()).isEqualByComparingTo("220.00");            // regla 38: bruta + cargos 47
        assertThat(ic.igv()).isEqualByComparingTo("39.60");
        assertThat(ic.precioVenta()).isEqualByComparingTo("259.60");
        assertThat(ic.precioVentaUnitario()).isEqualByComparingTo("129.8000000000");   // regla 33 (3270): (220 + 39.60) / 2
        assertThat(ic.valorUnitario()).isEqualByComparingTo("100.0000000000");        // el cargo no cambia el valor unitario
        assertThat(ic.cargoNoAfectaBase()).isEqualByComparingTo("0.00");
    }

    @Test void cargoDeLineaQueNoAfectaLaBase_codigo48() {
        // Se cobra sin IGV: no toca valor de venta ni IGV, suma al precio de venta y a ChargeTotalAmount.
        ItemCalculado ic = ItemCalculado.de(gravado("118.00", "1", Cargo.monto("48", new BigDecimal("15.00"))));
        assertThat(ic.valorVenta()).isEqualByComparingTo("100.00");
        assertThat(ic.igv()).isEqualByComparingTo("18.00");
        assertThat(ic.cargoNoAfectaBase()).isEqualByComparingTo("15.00");
        assertThat(ic.precioVenta()).isEqualByComparingTo("133.00");
        assertThat(ic.precioVentaUnitario()).isEqualByComparingTo("133.0000000000");   // 3270: + cargos 48
        Totales t = Totales.calcular(List.of(ic.item()));
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("118.00");
        assertThat(t.totalCargos()).isEqualByComparingTo("15.00");                        // 3301
        assertThat(t.total()).isEqualByComparingTo("133.00");                             // 3280
    }

    @Test void descuentoYCargoEnLaMismaLinea() {
        // base 200 − descuento 00 de 20 + cargo 47 de 10 = 190; IGV 34.20; cargo 48 de 5 → paga 229.20
        Item i = new Item("P", "Prod", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO,
                Descuento.porcentaje(new BigDecimal("10"), true), null, false,
                List.of(Cargo.porcentaje("47", new BigDecimal("5")), Cargo.monto("48", new BigDecimal("5.00"))));
        ItemCalculado ic = ItemCalculado.de(i);
        assertThat(ic.valorVenta()).isEqualByComparingTo("190.00");
        assertThat(ic.igv()).isEqualByComparingTo("34.20");
        assertThat(ic.precioVenta()).isEqualByComparingTo("229.20");
    }

    @Test void cargoGlobalQueAfectaLaBase_codigo49() {
        // Gravado 100 + 200, exonerado 50; cargo global 10 % sobre la base gravada → +30 a la base y al IGV (3277, 3278, 3291).
        Totales t = Totales.calcular(List.of(gravado("118.00", "1"), gravado("236.00", "1"),
                new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                null, List.of(Cargo.porcentaje("49", new BigDecimal("10"))), List.of(), Icbper.tasaVigente(java.time.LocalDate.of(2026, 1, 1)));
        assertThat(t.cargosGlobales()).hasSize(1);
        assertThat(t.cargosGlobales().get(0).base()).isEqualByComparingTo("300.00");
        assertThat(t.cargosGlobales().get(0).monto()).isEqualByComparingTo("30.00");
        assertThat(t.gravado()).isEqualByComparingTo("330.00");
        assertThat(t.igv()).isEqualByComparingTo("59.40");
        assertThat(t.exonerado()).isEqualByComparingTo("50.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("380.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("439.40");
        assertThat(t.totalCargos()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("439.40");
    }

    @Test void cargosGlobalesQueNoAfectanLaBase_codigos46y50() {
        // Recargo al consumo 10 % (46) y flete fijo 20.00 (50) sobre la base onerosa (gravado 100 + exonerado 50): sin IGV, suman al total.
        Totales t = Totales.calcular(List.of(gravado("118.00", "1"),
                new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                null, List.of(Cargo.porcentaje("46", new BigDecimal("10")), Cargo.monto("50", new BigDecimal("20.00"))), List.of(), Icbper.tasaVigente(java.time.LocalDate.of(2026, 1, 1)));
        assertThat(t.cargosGlobales()).extracting(CargoCalculado::monto).map(BigDecimal::toPlainString).containsExactly("15.00", "20.00");
        assertThat(t.cargosGlobales().get(0).base()).isEqualByComparingTo("150.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("150.00");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("168.00");
        assertThat(t.totalCargos()).isEqualByComparingTo("35.00");
        assertThat(t.total()).isEqualByComparingTo("203.00");
    }

    @Test void descuentoGlobal02YCargoGlobal49SeAplicanSobreLaBaseBruta() {
        // Base gravada 300: −10 % (30) + cargo 49 de 60 → base neta 330 e IGV 59.40 (3277 suma y resta desde los brutos).
        Totales t = Totales.calcular(List.of(gravado("354.00", "1")), Descuento.porcentaje(new BigDecimal("10"), true),
                List.of(Cargo.monto("49", new BigDecimal("60.00"))), List.of(), Icbper.tasaVigente(java.time.LocalDate.of(2026, 1, 1)));
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("300.00");
        assertThat(t.cargosGlobales().get(0).base()).isEqualByComparingTo("300.00");
        assertThat(t.gravado()).isEqualByComparingTo("330.00");
        assertThat(t.igv()).isEqualByComparingTo("59.40");
        assertThat(t.total()).isEqualByComparingTo("389.40");
    }

    @Test void cargoGlobalConAnticipo() {
        // Cargo 50 (no afecta la base) de 10.00 y anticipo de 50 sin IGV: total = 118 + 10 − 59 (3280).
        Totales t = Totales.calcular(List.of(gravado("118.00", "1")), null, List.of(Cargo.monto("50", new BigDecimal("10.00"))),
                List.of(new Anticipo("F001", 1, new BigDecimal("50.00"), Anticipo.Afectacion.GRAVADO, null)), Icbper.tasaVigente(java.time.LocalDate.of(2026, 1, 1)));
        assertThat(t.gravado()).isEqualByComparingTo("50.00");
        assertThat(t.totalAnticipos()).isEqualByComparingTo("59.00");
        assertThat(t.total()).isEqualByComparingTo("69.00");
    }

    @Test void elCodigoLoDerivaElDominio() {
        assertThat(Cargo.deLinea(true, Cargo.Tipo.MONTO, BigDecimal.ONE).codigo()).isEqualTo("47");
        assertThat(Cargo.deLinea(false, Cargo.Tipo.MONTO, BigDecimal.ONE).codigo()).isEqualTo("48");
        assertThat(Cargo.global(true, null, Cargo.Tipo.MONTO, BigDecimal.ONE).codigo()).isEqualTo("49");
        assertThat(Cargo.global(false, null, Cargo.Tipo.MONTO, BigDecimal.ONE).codigo()).isEqualTo("50");
        Cargo recargo = Cargo.global(false, Cargo.Motivo.RECARGO_CONSUMO, Cargo.Tipo.PORCENTAJE, BigDecimal.TEN);
        assertThat(recargo.codigo()).isEqualTo("46");
        assertThat(recargo.motivo()).contains(Cargo.Motivo.RECARGO_CONSUMO);
        assertThat(recargo.afectaBaseIgv()).isFalse();
        assertThat(Cargo.monto("50", BigDecimal.ONE).motivo()).isEmpty();
    }

    /** Cada regla SUNAT que rechaza un cargo, con el código que debe llevar el mensaje. */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("cargosInvalidos")
    void validaciones(String caso, String reglaEsperada, ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOf(DomainException.class).hasMessageContaining(reglaEsperada)
                .extracting("codigo").isEqualTo("CARGO_INVALIDO");
    }

    static Stream<Arguments> cargosInvalidos() {
        Item gravado = gravado("118.00", "1");
        Item exonerado = new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO);
        return Stream.of(
                Arguments.of("FISE (45) no soportado", "2954", (ThrowingCallable) () -> Cargo.monto("45", BigDecimal.ONE)),
                Arguments.of("código de descuento", "2954", (ThrowingCallable) () -> Cargo.monto("00", BigDecimal.ONE)),
                Arguments.of("monto cero", "2955", (ThrowingCallable) () -> Cargo.monto("47", BigDecimal.ZERO)),
                Arguments.of("monto con 3 decimales", "2955", (ThrowingCallable) () -> Cargo.monto("47", new BigDecimal("1.005"))),
                Arguments.of("porcentaje de 4 enteros", "3052", (ThrowingCallable) () -> Cargo.porcentaje("47", new BigDecimal("1000"))),
                Arguments.of("porcentaje con 6 decimales", "2955", (ThrowingCallable) () -> Cargo.porcentaje("47", new BigDecimal("0.000001"))),
                // Un porcentaje ínfimo sobre una base pequeña redondea a 0.00: SUNAT rechaza Amount = 0.
                Arguments.of("porcentaje que redondea a 0.00", "2955", (ThrowingCallable) () -> ItemCalculado.de(gravado("1.18", "1", Cargo.porcentaje("48", new BigDecimal("0.1"))))),
                Arguments.of("código global en una línea", "4268", (ThrowingCallable) () -> gravado("118.00", "1", Cargo.monto("50", BigDecimal.ONE))),
                Arguments.of("código de línea en global", "4291", (ThrowingCallable) () -> Totales.calcular(List.of(gravado), null, List.of(Cargo.monto("47", BigDecimal.ONE)), List.of(), BigDecimal.ZERO)),
                Arguments.of("49 sin ítems gravados", "gravados", (ThrowingCallable) () -> Totales.calcular(List.of(exonerado), null, List.of(Cargo.monto("49", BigDecimal.ONE)), List.of(), BigDecimal.ZERO)),
                Arguments.of("recargo al consumo que afecta la base", "no afecta la base", (ThrowingCallable) () -> Cargo.global(true, Cargo.Motivo.RECARGO_CONSUMO, Cargo.Tipo.MONTO, BigDecimal.ONE)),
                Arguments.of("cargo en una gratuita", "gratuita", (ThrowingCallable) () -> ItemCalculado.de(new Item("G", "Gratis", "NIU", BigDecimal.ONE, new BigDecimal("50.00"),
                        TipoAfectacionIgv.porCodigo("11"), null, null, false, List.of(Cargo.monto("48", BigDecimal.ONE))))));
    }
}
