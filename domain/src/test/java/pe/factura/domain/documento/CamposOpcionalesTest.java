package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
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

/** Fecha de vencimiento (campo 8), código de producto SUNAT (28), GTIN (29) y redondeo del importe total (56, regla 3303). */
class CamposOpcionalesTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));

    private static Item gravado(String precio, String codigoSunat, Gtin gtin) {
        return new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal(precio), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de(codigoSunat), gtin);
    }

    private static Comprobante factura(LocalDate vencimiento, BigDecimal redondeo, Item... items) {
        return Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 18), vencimiento, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null), List.of(items), FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, redondeo, CLOCK);
    }

    @Test void gtinYCodigoSunat() {
        Item i = gravado("118.00", "15101505", new Gtin("GTIN-13", "7750182000123"));
        assertThat(i.codigoSunat().codigo()).isEqualTo("15101505");
        assertThat(i.gtin().tipo()).isEqualTo("GTIN-13");
        assertThat(gravado("118.00", null, null).tieneCodigoSunat()).isFalse();
        assertThatThrownBy(() -> new Gtin("GTIN-13", "775018200012")).isInstanceOf(DomainException.class).hasMessageContaining("4334");
        assertThatThrownBy(() -> new Gtin("GTIN-8", "1234567A")).hasMessageContaining("4334");
        assertThatThrownBy(() -> new Gtin("EAN-13", "7750182000123")).hasMessageContaining("4335");
        assertThat(new Gtin("GTIN-14", "17750182000120").codigo()).hasSize(14);
        assertThatThrownBy(() -> gravado("118.00", "1510150", null)).hasMessageContaining("3496");
        assertThatThrownBy(() -> gravado("118.00", "00000000", null)).hasMessageContaining("3496");
        assertThatThrownBy(() -> gravado("118.00", "99999999", null)).hasMessageContaining("3496");
        assertThatThrownBy(() -> gravado("118.00", "ABCDEFGH", null)).hasMessageContaining("3496");
    }

    @Test void fechaDeVencimiento() {
        assertThat(factura(LocalDate.of(2026, 10, 18), null, gravado("118.00", null, null)).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(factura(LocalDate.of(2026, 9, 18), null, gravado("118.00", null, null)).fechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 18));   // el mismo día vale
        assertThat(factura(null, null, gravado("118.00", null, null)).fechaVencimiento()).isNull();
        assertThatThrownBy(() -> factura(LocalDate.of(2026, 9, 17), null, gravado("118.00", null, null))).extracting("codigo").isEqualTo("FECHA_INVALIDA");
    }

    @Test void redondeoDelImporteTotal() {
        // 118.37 − 0.37 = 118.00: el redondeo entra en el total (3280) pero no en el precio de venta ni en el IGV.
        Comprobante c = factura(null, new BigDecimal("-0.37"), gravado("118.37", null, null));
        assertThat(c.totales().totalPrecioVenta()).isEqualByComparingTo("118.37");
        assertThat(c.totales().redondeo()).isEqualByComparingTo("-0.37");
        assertThat(c.totales().tieneRedondeo()).isTrue();
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        assertThat(factura(null, new BigDecimal("0.5"), gravado("118.50", null, null)).totales().total()).isEqualByComparingTo("119.00");
        assertThat(factura(null, BigDecimal.ZERO, gravado("118.50", null, null)).totales().tieneRedondeo()).isFalse();
        assertThatThrownBy(() -> factura(null, new BigDecimal("1.01"), gravado("118.00", null, null))).hasMessageContaining("3303");
        assertThatThrownBy(() -> factura(null, new BigDecimal("-1.01"), gravado("118.00", null, null))).hasMessageContaining("3303");
        assertThatThrownBy(() -> factura(null, new BigDecimal("0.001"), gravado("118.00", null, null))).hasMessageContaining("3303");
        assertThat(factura(null, BigDecimal.ONE, gravado("118.00", null, null)).totales().total()).isEqualByComparingTo("119.00");
        // El redondeo no puede dejar el total en negativo (un total de 0.50 con −1.00).
        assertThatThrownBy(() -> factura(null, new BigDecimal("-1"), gravado("0.50", null, null))).extracting("codigo").isEqualTo("REDONDEO_INVALIDO");
        // Las cuotas al crédito se validan contra el total ya redondeado.
        Comprobante credito = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 18), null, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE SAC", null), List.of(gravado("118.37", null, null)),
                FormaPago.credito(new BigDecimal("118.00"), List.of(new FormaPago.Cuota(new BigDecimal("118.00"), LocalDate.of(2026, 10, 18)))),
                null, List.of(), null, null, null, List.of(), null, new BigDecimal("-0.37"), CLOCK);
        assertThat(credito.totales().total()).isEqualByComparingTo("118.00");
    }
}
