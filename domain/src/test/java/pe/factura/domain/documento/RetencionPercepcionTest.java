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

/** Retención del IGV (código 62, reglas 3263/3264) y percepción (51/52/53, reglas 2788, 2797, 2798, 3093, 3308, 3330). */
class RetencionPercepcionTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Comprobante factura(String moneda, String op, FormaPago fp, RetencionIgv r, Percepcion p) {
        return Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), moneda, op,
                new Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                fp, null, null, r, p, CLOCK);
    }

    static void rechaza(Runnable r, String codigo, String sunat) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).hasMessageStartingWith(sunat + " -").extracting("codigo").isEqualTo(codigo);
    }

    @Test void retencionCalculadaAlTresPorCiento() {
        Comprobante c = factura("PEN", "0101", FormaPago.contado(), new RetencionIgv(null, null), null);
        assertThat(c.retencion().porcentaje()).isEqualByComparingTo("3");
        assertThat(c.retencion().monto()).isEqualByComparingTo("35.40");
        assertThat(c.retencion().factor()).isEqualByComparingTo("0.03000");
        assertThat(c.totales().total()).isEqualByComparingTo("1180.00");   // no altera los totales
    }

    @Test void retencionConMontoFueraDeTolerancia_3263() {
        rechaza(() -> factura("PEN", "0101", FormaPago.contado(), new RetencionIgv(new BigDecimal("3"), new BigDecimal("40.00")), null), "RETENCION_INVALIDA", "3263");
        factura("PEN", "0101", FormaPago.contado(), new RetencionIgv(new BigDecimal("3"), new BigDecimal("36.00")), null);   // ±1 admitido
    }

    @Test void percepcionVentaInternaAlDosPorCiento() {
        Comprobante c = factura("PEN", "2001", FormaPago.contado(), null, new Percepcion("51", null, null, null));
        assertThat(c.percepcion().porcentaje()).isEqualByComparingTo("2");
        assertThat(c.percepcion().base()).isEqualByComparingTo("1180.00");
        assertThat(c.percepcion().monto()).isEqualByComparingTo("23.60");
        assertThat(c.percepcion().totalConPercepcion(c.totales().total())).isEqualByComparingTo("1203.60");
        assertThat(c.percepcion().descripcionRegimen()).containsIgnoringCase("venta interna");
        assertThat(c.totales().total()).isEqualByComparingTo("1180.00");   // PayableAmount sin percepción
    }

    @Test void tasaDelRegimenLaFijaElCatalogo22() {
        assertThat(new Percepcion("52", null, null, null).porcentaje()).isEqualByComparingTo("1");
        assertThat(new Percepcion("53", null, null, null).porcentaje()).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> new Percepcion("51", new BigDecimal("3"), null, null)).hasMessageContaining("catálogo 22");
        rechaza(() -> new Percepcion("54", null, null, null), "PERCEPCION_INVALIDA", "3071");
    }

    @Test void percepcionExigeOperacion2001ContadoYSoles() {
        rechaza(() -> factura("PEN", "0101", FormaPago.contado(), null, new Percepcion("51", null, null, null)), "PERCEPCION_INVALIDA", "3308");
        rechaza(() -> factura("PEN", "2001", FormaPago.credito(new BigDecimal("1180.00"), List.of(new FormaPago.Cuota(new BigDecimal("1180.00"), LocalDate.of(2026, 10, 1)))), null,
                new Percepcion("51", null, null, null)), "PERCEPCION_INVALIDA", "3330");
        rechaza(() -> factura("USD", "2001", FormaPago.contado(), null, new Percepcion("51", null, null, null)), "PERCEPCION_INVALIDA", "2788");
        rechaza(() -> factura("PEN", "2001", FormaPago.contado(), null, null), "PERCEPCION_INVALIDA", "3093");
    }

    @Test void baseYMontoDePercepcionCoherentes() {
        rechaza(() -> factura("PEN", "2001", FormaPago.contado(), null, new Percepcion("51", null, new BigDecimal("2000.00"), null)), "PERCEPCION_INVALIDA", "2797");
        rechaza(() -> factura("PEN", "2001", FormaPago.contado(), null, new Percepcion("51", null, null, new BigDecimal("30.00"))), "PERCEPCION_INVALIDA", "2798");
        Comprobante c = factura("PEN", "2001", FormaPago.contado(), null, new Percepcion("51", null, new BigDecimal("1000.00"), new BigDecimal("20.50")));
        assertThat(c.percepcion().monto()).isEqualByComparingTo("20.50");   // dentro de ±1 se respeta lo enviado
    }
}
