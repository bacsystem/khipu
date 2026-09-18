package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;

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

    @Test void validaciones() {
        assertThatThrownBy(() -> Cargo.monto("45", BigDecimal.ONE)).isInstanceOf(DomainException.class).hasMessageContaining("2954");   // FISE no soportado
        assertThatThrownBy(() -> Cargo.monto("00", BigDecimal.ONE)).hasMessageContaining("2954");
        assertThatThrownBy(() -> Cargo.monto("47", BigDecimal.ZERO)).hasMessageContaining("2955");
        assertThatThrownBy(() -> Cargo.monto("47", new BigDecimal("1.005"))).hasMessageContaining("2955");
        assertThatThrownBy(() -> Cargo.porcentaje("47", new BigDecimal("1000"))).hasMessageContaining("3052");
        assertThatThrownBy(() -> Cargo.porcentaje("47", new BigDecimal("0.000001"))).hasMessageContaining("2955");
        // Un cargo de porcentaje ínfimo sobre una base pequeña redondea a 0.00: SUNAT rechaza Amount = 0.
        assertThatThrownBy(() -> ItemCalculado.de(gravado("1.18", "1", Cargo.porcentaje("48", new BigDecimal("0.1"))))).hasMessageContaining("2955");
        // Nivel equivocado (4268 / 4291).
        assertThatThrownBy(() -> gravado("118.00", "1", Cargo.monto("50", BigDecimal.ONE))).extracting("codigo").isEqualTo("CARGO_INVALIDO");
        assertThatThrownBy(() -> Totales.calcular(List.of(gravado("118.00", "1")), null, List.of(Cargo.monto("47", BigDecimal.ONE)), List.of(), BigDecimal.ZERO))
                .hasMessageContaining("4291");
        // 49 exige base gravada; una gratuita no admite cargos.
        assertThatThrownBy(() -> Totales.calcular(List.of(new Item("E", "Exo", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                null, List.of(Cargo.monto("49", BigDecimal.ONE)), List.of(), BigDecimal.ZERO)).hasMessageContaining("gravados");
        assertThatThrownBy(() -> ItemCalculado.de(new Item("G", "Gratis", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.porCodigo("11"), null, null, false,
                List.of(Cargo.monto("48", BigDecimal.ONE))))).hasMessageContaining("gratuita");
    }
}
