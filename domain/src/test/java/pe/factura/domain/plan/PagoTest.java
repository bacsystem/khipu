package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un pago que un administrador registra a mano (#194): lo que dice, y lo que no puede decir. */
class PagoTest {
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    static Pago pago(LocalDate desde, LocalDate hasta, String monto, String referencia, String nota) {
        return Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), desde, hasta, new BigDecimal(monto), MedioDePago.YAPE, LocalDate.of(2026, 10, 14), referencia, nota, AHORA, null);
    }

    static Pago pago() { return pago(DESDE, HASTA, "29.00", "OP-123", "Pagó por Yape"); }

    static void rechaza(String codigo, Runnable r) { assertThatThrownBy(r::run).isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).codigo()).isEqualTo(codigo); }

    @Test void unPagoGuardaLoQueSeLeDijo() {
        UUID id = UUID.randomUUID();
        UUID cuenta = UUID.randomUUID();
        UUID suscripcion = UUID.randomUUID();

        Pago p = Pago.registrar(id, cuenta, suscripcion, DESDE, HASTA, new BigDecimal("29.00"), MedioDePago.TRANSFERENCIA, LocalDate.of(2026, 10, 14), "OP-9", "nota", AHORA, null);

        assertThat(p.id()).isEqualTo(id);
        assertThat(p.cuentaId()).isEqualTo(cuenta);
        assertThat(p.suscripcionId()).isEqualTo(suscripcion);
        assertThat(p.periodoDesde()).isEqualTo(DESDE);
        assertThat(p.periodoHasta()).isEqualTo(HASTA);
        assertThat(p.monto()).isEqualByComparingTo("29.00");
        assertThat(p.medio()).isEqualTo(MedioDePago.TRANSFERENCIA);
        assertThat(p.fechaDePago()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(p.referencia()).isEqualTo("OP-9");
        assertThat(p.nota()).isEqualTo("nota");
        assertThat(p.registradoEn()).isEqualTo(AHORA);
        assertThat(p.extendioHasta()).isNull();
    }

    // --- el periodo -------------------------------------------------------------------------------------------------------------------------

    @Test void elPeriodoPuedeSerDeUnSoloDiaPeroNoAlReves() {
        assertThat(pago(DESDE, DESDE, "1", null, null).periodoHasta()).isEqualTo(DESDE);
        rechaza("PERIODO_INVALIDO", () -> pago(HASTA, DESDE, "1", null, null));
    }

    @Test void elPeriodoNoPasaDeUnAnio() {
        assertThat(pago(DESDE, LocalDate.of(2027, 9, 30), "1", null, null)).isNotNull();
        rechaza("PERIODO_INVALIDO", () -> pago(DESDE, LocalDate.of(2027, 10, 1), "1", null, null));
    }

    @Test void sinPeriodoNoHayPago() {
        rechaza("PERIODO_INVALIDO", () -> pago(null, HASTA, "1", null, null));
        rechaza("PERIODO_INVALIDO", () -> pago(DESDE, null, "1", null, null));
    }

    // --- el monto ---------------------------------------------------------------------------------------------------------------------------

    @Test void elMontoEsPositivoConHastaDosDecimalesYSeNormalizaADos() {
        assertThat(pago(DESDE, HASTA, "29", null, null).monto().toPlainString()).isEqualTo("29.00");
        assertThat(pago(DESDE, HASTA, "0.01", null, null).monto().toPlainString()).isEqualTo("0.01");
        assertThat(pago(DESDE, HASTA, "29.50", null, null).monto().toPlainString()).isEqualTo("29.50");
        assertThat(pago(DESDE, HASTA, "29.500", null, null).monto().toPlainString()).as("ceros de más no cuentan como decimales").isEqualTo("29.50");
    }

    @Test void elMontoNoPuedeSerCeroNegativoNiTenerTresDecimales() {
        for (String malo : new String[]{"0", "0.00", "-1", "-0.01", "29.005", "0.001"}) rechaza("MONTO_INVALIDO", () -> pago(DESDE, HASTA, malo, null, null));
    }

    @Test void elMontoTieneUnTope() {
        assertThat(pago(DESDE, HASTA, "9999999.99", null, null)).isNotNull();
        rechaza("MONTO_INVALIDO", () -> pago(DESDE, HASTA, "10000000.00", null, null));
    }

    @Test void sinMontoNoHayPago() {
        rechaza("MONTO_INVALIDO", () -> Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DESDE, HASTA, null, MedioDePago.YAPE, LocalDate.of(2026, 10, 14), null, null, AHORA, null));
    }

    // --- el medio y la fecha ----------------------------------------------------------------------------------------------------------------

    @Test void sinMedioNoHayPago() {
        rechaza("MEDIO_INVALIDO", () -> Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DESDE, HASTA, BigDecimal.TEN, null, LocalDate.of(2026, 10, 14), null, null, AHORA, null));
    }

    @Test void sinFechaDePagoNoHayPago() {
        rechaza("FECHA_DE_PAGO_INVALIDA", () -> Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, null, null, null, AHORA, null));
    }

    // --- referencia y nota ------------------------------------------------------------------------------------------------------------------

    @Test void laReferenciaYLaNotaSonOpcionalesSeRecortanYUnaVaciaEsNinguna() {
        assertThat(pago(DESDE, HASTA, "1", "  OP-1  ", "  ok  ").referencia()).isEqualTo("OP-1");
        assertThat(pago(DESDE, HASTA, "1", "  OP-1  ", "  ok  ").nota()).isEqualTo("ok");
        assertThat(pago(DESDE, HASTA, "1", "   ", "").referencia()).isNull();
        assertThat(pago(DESDE, HASTA, "1", "   ", "").nota()).isNull();
        assertThat(pago(DESDE, HASTA, "1", null, null).referencia()).isNull();
    }

    @Test void laReferenciaTieneCienCaracteresYLaNotaDoscientos() {
        assertThat(pago(DESDE, HASTA, "1", "r".repeat(100), "n".repeat(200))).isNotNull();
        rechaza("REFERENCIA_INVALIDA", () -> pago(DESDE, HASTA, "1", "r".repeat(101), null));
        rechaza("NOTA_INVALIDA", () -> pago(DESDE, HASTA, "1", null, "n".repeat(201)));
    }

    @Test void losLimitesSeMidenDespuesDeRecortar() {
        assertThat(pago(DESDE, HASTA, "1", " " + "r".repeat(100) + " ", null).referencia()).hasSize(100);
    }

    // --- ids --------------------------------------------------------------------------------------------------------------------------------

    @Test void unPagoNecesitaSuCuentaSuSuscripcionYSuInstante() {
        rechaza("PAGO_INVALIDO", () -> Pago.registrar(null, UUID.randomUUID(), UUID.randomUUID(), DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, LocalDate.of(2026, 10, 14), null, null, AHORA, null));
        rechaza("PAGO_INVALIDO", () -> Pago.registrar(UUID.randomUUID(), null, UUID.randomUUID(), DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, LocalDate.of(2026, 10, 14), null, null, AHORA, null));
        rechaza("PAGO_INVALIDO", () -> Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), null, DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, LocalDate.of(2026, 10, 14), null, null, AHORA, null));
        rechaza("PAGO_INVALIDO", () -> Pago.registrar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, LocalDate.of(2026, 10, 14), null, null, null, null));
    }

    // --- extender el vencimiento ------------------------------------------------------------------------------------------------------------

    /** «Pagado hasta el 31 de octubre» es la medianoche del 1 de noviembre en Lima (UTC-5): el vencimiento es exclusivo, como en la suscripción. */
    @Test void elPagoVenceraElDiaSiguienteAlFinDelPeriodoALaMedianocheDeLima() {
        assertThat(pago().venceriaEn()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(pago(DESDE, LocalDate.of(2026, 12, 31), "1", null, null).venceriaEn()).isEqualTo(Instant.parse("2027-01-01T05:00:00Z"));
    }

    @Test void unPagoPuedeAnotarHastaDondeExtendioElVencimiento() {
        Pago p = pago().conExtension(Instant.parse("2026-11-01T05:00:00Z"));

        assertThat(p.extendioHasta()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(p.monto()).isEqualByComparingTo("29.00");
        assertThat(p.id()).isEqualTo(p.id());
    }

    @Test void unPagoSinExtensionNoMueveElVencimientoQueDice() {
        assertThat(pago().extendioHasta()).isNull();
    }
}
