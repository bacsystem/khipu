package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tasa general 18 % y tasa especial del padrón de restaurantes y hoteles: 10 % desde 2023, 10.5 % desde el 2026-02-13. */
class TasaIgvTest {

    @Test void sinTasaEspecialSiempreEsLaGeneral() {
        assertThat(TasaIgv.vigente(LocalDate.of(2022, 6, 1), false)).isEqualByComparingTo("18.00");
        assertThat(TasaIgv.vigente(LocalDate.of(2026, 9, 19), false)).isEqualByComparingTo("18.00");
    }

    @Test void laTasaEspecialDependeDeLaFechaDeEmision() {
        assertThat(TasaIgv.vigente(LocalDate.of(2022, 12, 31), true)).isEqualByComparingTo("18.00");   // antes del padrón
        assertThat(TasaIgv.vigente(LocalDate.of(2023, 1, 1), true)).isEqualByComparingTo("10.00");
        assertThat(TasaIgv.vigente(LocalDate.of(2026, 2, 12), true)).isEqualByComparingTo("10.00");
        assertThat(TasaIgv.vigente(LocalDate.of(2026, 2, 13), true)).isEqualByComparingTo("10.50");
        assertThat(TasaIgv.vigente(LocalDate.of(2026, 9, 19), true)).isEqualByComparingTo("10.50");
    }

    @Test void factorYNormalizacion() {
        assertThat(TasaIgv.factor(new BigDecimal("10.50"))).isEqualByComparingTo("0.105");
        assertThat(TasaIgv.normalizar(null)).isEqualByComparingTo("18.00");
        assertThat(TasaIgv.normalizar(new BigDecimal("10.5"))).isEqualTo(new BigDecimal("10.50"));
    }

    /** Con 10.5 % el precio con IGV se desglosa a esa tasa y el cbc:Percent de la línea la declara (reglas 3291, 3462). */
    @Test void losTotalesUsanLaTasaDelComprobante() {
        Item gravado = new Item("P1", "Menú", "NIU", BigDecimal.ONE, new BigDecimal("110.50"), TipoAfectacionIgv.GRAVADO);
        Item exonerado = new Item("P2", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO);
        Totales t = Totales.calcular(List.of(gravado, exonerado), null, List.of(), List.of(), Icbper.tasaVigente(LocalDate.of(2026, 9, 19)), null, new BigDecimal("10.50"));
        assertThat(t.tasaIgv()).isEqualByComparingTo("10.50");
        assertThat(t.gravado()).isEqualByComparingTo("100.00");
        assertThat(t.igv()).isEqualByComparingTo("10.50");
        assertThat(t.total()).isEqualByComparingTo("160.50");
        assertThat(t.items().get(0).porcentajeIgv()).isEqualByComparingTo("10.50");
        assertThat(t.items().get(1).porcentajeIgv()).isEqualByComparingTo("0.00");
        // Un anticipo gravado regularizado en esta factura también paga IGV al 10.5 %.
        Totales conAnticipo = Totales.calcular(List.of(gravado), null, List.of(), List.of(new Anticipo("F001", 1, new BigDecimal("40.00"), Anticipo.Afectacion.GRAVADO, null)),
                Icbper.tasaVigente(LocalDate.of(2026, 9, 19)), null, new BigDecimal("10.50"));
        assertThat(conAnticipo.anticipos().get(0).importePagado()).isEqualByComparingTo("44.20");
        assertThat(conAnticipo.igv()).isEqualByComparingTo("6.30");     // (100 − 40) × 10.5 %
        assertThat(conAnticipo.total()).isEqualByComparingTo("66.30");  // 110.50 − 44.20
    }

    @Test void porDefectoSigueSiendoElDieciocho() {
        Totales t = Totales.calcular(List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)));
        assertThat(t.tasaIgv()).isEqualByComparingTo("18.00");
        assertThat(t.igv()).isEqualByComparingTo("18.00");
    }
}
