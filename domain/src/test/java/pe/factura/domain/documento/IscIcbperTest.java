package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo("200.30");   // regla 3277: suma de LineExtensionAmount, sin ISC
        assertThat(t.subtotales().get(0).impuesto()).isEqualByComparingTo("42.35"); // regla 3291: sobre las bases de línea con ISC
        assertThat(t.subtotales().get(1).base()).isEqualByComparingTo("100.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("200.30");
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("279.15"); // 200.30 + 35 + 1.50 + 42.35 (regla 55)
        assertThat(t.total()).isEqualByComparingTo("279.15");
    }

    /** Descuento global 02 con ISC: la base neta va sin ISC (3277) y el IGV se recalcula sobre base neta + ISC (3291). */
    @Test void descuentoGlobal02ConIsc() {
        Totales t = Totales.calcular(List.of(item("159.30", "1", new Isc("01", new BigDecimal("35"), null), false)),
                Descuento.porcentaje(BigDecimal.TEN, true), new BigDecimal("0.50"));
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("100.00");
        assertThat(t.gravado()).isEqualByComparingTo("90.00");
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo("90.00");
        assertThat(t.igv()).isEqualByComparingTo("22.50");                 // (90 + 35) × 18 %
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("147.50");   // 90 + 35 + 22.50
    }

    @Test void iscInvalido() {
        assertThatThrownBy(() -> new Isc("04", BigDecimal.TEN, null)).isInstanceOf(DomainException.class).hasMessageContaining("2041");
        assertThatThrownBy(() -> new Isc("03", BigDecimal.TEN, null)).hasMessageContaining("03").hasMessageContaining("base_pvp");
        assertThatThrownBy(() -> new Isc("01", null, null)).hasMessageContaining("3104");
        assertThatThrownBy(() -> new Isc("02", BigDecimal.TEN, BigDecimal.ONE)).hasMessageContaining("no lleva tasa");
    }

    /** Sistema 03 (#68): la base del ISC es el PVP sugerido × cantidad, el ISC = base × tasa, y el IGV va sobre valor de venta + ISC (regla 204). */
    @Test void iscAlValorSegunPrecioDeVentaAlPublico() {
        // PVP sugerido 3.50, tasa 30 % → ISC 1.05 por unidad; precio con IGV e ISC = (2.00 + 1.05) × 1.18 = 3.599 → 3.60
        Isc isc = new Isc("03", new BigDecimal("30"), null, new BigDecimal("3.50"));
        assertThat(isc.montoUnitarioEfectivo()).isEqualByComparingTo("1.05");
        Item cerveza = new Item("C1", "Cerveza 620 ml", "NIU", new BigDecimal("10"), new BigDecimal("3.599"), TipoAfectacionIgv.GRAVADO, null, isc, false);
        ItemCalculado ic = ItemCalculado.de(cerveza);
        assertThat(ic.valorVenta()).isEqualByComparingTo("20.00");
        assertThat(ic.iscBase()).isEqualByComparingTo("35.00");     // 3.50 × 10, no el valor de venta
        assertThat(ic.isc()).isEqualByComparingTo("10.50");         // 35 × 30 %
        assertThat(ic.iscPorcentaje()).isEqualByComparingTo("30");
        assertThat(ic.igv()).isEqualByComparingTo("5.49");          // (20 + 10.50) × 18 %
        assertThat(ic.precioVenta()).isEqualByComparingTo("35.99");
        Totales t = Totales.calcular(List.of(cerveza));
        assertThat(t.subtotales()).filteredOn(st -> st.tributo() == Tributo.ISC).first().satisfies(st -> {
            assertThat(st.base()).isEqualByComparingTo("35.00");
            assertThat(st.impuesto()).isEqualByComparingTo("10.50");
        });
        assertThat(t.igv()).isEqualByComparingTo("5.49");
        // Validaciones: base_pvp obligatoria y no menor que el valor unitario; los otros sistemas no la llevan.
        assertThatThrownBy(() -> new Isc("03", new BigDecimal("30"), null, null)).isInstanceOf(DomainException.class).hasMessageContaining("base_pvp");
        assertThatThrownBy(() -> new Isc("01", new BigDecimal("30"), null, new BigDecimal("3.50"))).hasMessageContaining("no lleva base_pvp");
    }

    /**
     * La regla del PVP sugerido (#68, 3108) se exige solo al emitir ({@link Comprobante.FacturaBuilder#crear}), no dentro de
     * {@link ItemCalculado#de}: así una factura ya emitida y persistida se puede seguir leyendo aunque la regla cambie después (#89).
     */
    @Test void elPvpSugeridoMenorQueElValorUnitarioSeExigeSoloAlEmitir() {
        Item cervezaPvpBajo = new Item("C1", "Cerveza", "NIU", BigDecimal.ONE, new BigDecimal("11.80"), TipoAfectacionIgv.GRAVADO, null, new Isc("03", new BigDecimal("30"), null, new BigDecimal("1.00")), false);
        ItemCalculado ic = ItemCalculado.de(cervezaPvpBajo);   // no lanza: la validación de negocio no vive aquí
        assertThatThrownBy(ic::exigirBasePvpValida).hasMessageContaining("PVP sugerido").hasMessageContaining("no puede ser menor");

        Receptor receptor = new Receptor("6", "20601234565", "CLIENTE SAC", null);
        java.time.Clock reloj = java.time.Clock.fixed(java.time.Instant.parse("2026-09-13T15:00:00Z"), java.time.ZoneId.of("America/Lima"));
        assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", receptor, List.of(cervezaPvpBajo)).crear(reloj))
                .isInstanceOf(DomainException.class).hasMessageContaining("PVP sugerido");

        Comprobante rehidratado = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), EstadoDocumento.ACEPTADO, receptor, List.of(cervezaPvpBajo))
                .firma("h", "n", "k").rehidratar();   // no lanza: rehidratar no revalida
        assertThat(rehidratado.totales().items().get(0).item().isc().basePvp()).isEqualByComparingTo("1.00");
    }
}
