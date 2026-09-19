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

/** Detracción (SPOT): reglas 3033, 3034, 3037, 3127, 3128, 3129, 3174 de la hoja Factura2_0 y redondeo del depósito al sol. */
class DetraccionTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Comprobante factura(String tipoOperacion, Detraccion d) {
        return Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", tipoOperacion, new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("11800.00"), TipoAfectacionIgv.GRAVADO))).detraccion(d).crear(CLOCK);
    }

    static void rechaza(Runnable r, String codigoSunat) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).hasMessageStartingWith(codigoSunat + " -").extracting("codigo").isEqualTo("DETRACCION_INVALIDA");
    }

    @Test void detraccionValidaEnOperacion1001() {
        Comprobante c = factura("1001", new Detraccion("022", new BigDecimal("12"), new BigDecimal("1416.00"), " 00-000-123456 ", null));
        assertThat(c.detraccion().medioPago()).isEqualTo("001");
        assertThat(c.detraccion().cuentaBancoNacion()).isEqualTo("00-000-123456");
        assertThat(c.detraccion().descripcionBienServicio()).containsIgnoringCase("servicios");
        assertThat(c.totales().total()).isEqualByComparingTo("11800.00");   // no altera los totales
    }

    /** Sin monto, el comprobante lo completa con total × % al sol (solo en PEN: 3208 en otra moneda). */
    @Test void montoSeCompletaContraElTotalDelComprobante() {
        Comprobante c = factura("1001", new Detraccion("022", new BigDecimal("12"), null, "cta", null));
        assertThat(c.detraccion().monto()).isEqualByComparingTo("1416.00");   // 11 800 × 12 %
        Comprobante conDescuento = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "1001", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("11800.00"), TipoAfectacionIgv.GRAVADO))).descuentoGlobal(Descuento.monto(new BigDecimal("1000.00"), false)).detraccion(new Detraccion("022", new BigDecimal("12"), null, "cta", null)).crear(CLOCK);
        assertThat(conDescuento.detraccion().monto()).isEqualByComparingTo("1296.00");   // sobre el importe a pagar (10 800), no sobre el precio de venta
        rechaza(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "USD", "1001", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).detraccion(new Detraccion("022", new BigDecimal("12"), null, "cta", null)).crear(CLOCK), "3208");
        assertThat(factura("1001", new Detraccion("022", new BigDecimal("12"), new BigDecimal("1400.00"), "cta", null)).detraccion().monto()).isEqualByComparingTo("1400.00");
    }

    @Test void montoRedondeadoAlSol() {
        assertThat(Detraccion.montoSobre(new BigDecimal("11800.00"), new BigDecimal("12"))).isEqualByComparingTo("1416.00");
        assertThat(Detraccion.montoSobre(new BigDecimal("1234.56"), new BigDecimal("10"))).isEqualByComparingTo("123.00");
        assertThat(Detraccion.montoSobre(new BigDecimal("1235.00"), new BigDecimal("10"))).isEqualByComparingTo("124.00");
    }

    @Test void codigoFueraDelCatalogo54_3033() {
        rechaza(() -> new Detraccion("999", BigDecimal.TEN, BigDecimal.TEN, "cta", null), "3033");
    }

    @Test void sinCuenta_3034() {
        // La cuenta puede omitirse para completarla con la de la empresa; sin ninguna, la factura se rechaza.
        Detraccion sinCuenta = new Detraccion("022", new BigDecimal("12"), new BigDecimal("1416.00"), " ", null);
        assertThat(sinCuenta.sinCuenta()).isTrue();
        rechaza(() -> factura("1001", sinCuenta), "3034");
        rechaza(() -> sinCuenta.conCuenta(null), "3034");
        assertThat(sinCuenta.conCuenta("00-000-123456").cuentaBancoNacion()).isEqualTo("00-000-123456");
        assertThat(factura("1001", sinCuenta.conCuenta("00-000-123456")).detraccion().sinCuenta()).isFalse();
    }

    @Test void montoNoPositivo_3037() {
        rechaza(() -> new Detraccion("022", BigDecimal.TEN, BigDecimal.ZERO, "cta", null), "3037");
    }

    @Test void medioDePagoFueraDelCatalogo59_3174() {
        rechaza(() -> new Detraccion("022", BigDecimal.TEN, BigDecimal.TEN, "cta", "ZZZ"), "3174");
    }

    @Test void operacion1001SinDetraccion_3127() {
        rechaza(() -> factura("1001", null), "3127");
    }

    @Test void detraccionConOperacion0101_3128() {
        rechaza(() -> factura("0101", new Detraccion("022", BigDecimal.TEN, BigDecimal.TEN, "cta", null)), "3128");
    }

    @Test void transporteDeCargaExigeCodigo027_3129() {
        rechaza(() -> factura("1004", new Detraccion("022", BigDecimal.TEN, BigDecimal.TEN, "cta", null)), "3129");
        factura("1004", new Detraccion("027", new BigDecimal("4"), new BigDecimal("472.00"), "cta", null));
    }
}
