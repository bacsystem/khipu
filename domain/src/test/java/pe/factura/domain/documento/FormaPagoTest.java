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

/** Cada regla lleva el código de la hoja Factura2_0 con el que SUNAT la rechazaría; aquí se rechaza antes de consumir numeración. */
class FormaPagoTest {
    static final LocalDate EMISION = LocalDate.of(2026, 9, 13);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static FormaPago.Cuota cuota(String monto, LocalDate fecha) { return new FormaPago.Cuota(new BigDecimal(monto), fecha); }

    /** Factura de 118.00 (100 + IGV) con la forma de pago dada. */
    static Comprobante factura(FormaPago fp) {
        return Comprobante.factura(UUID.randomUUID(), "F001", EMISION, "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).formaPago(fp).crear(CLOCK);
    }

    static void rechaza(Runnable r, String codigoSunat) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
                .hasMessageStartingWith(codigoSunat + " -")
                .extracting("codigo").isEqualTo("FORMA_PAGO_INVALIDA");
    }

    @Test void contadoPorDefecto() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", EMISION, "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        assertThat(c.formaPago()).isEqualTo(FormaPago.contado());
        assertThat(c.formaPago().esCredito()).isFalse();
    }

    @Test void creditoValidoConDosCuotas() {
        FormaPago fp = FormaPago.credito(new BigDecimal("118.00"),
                List.of(cuota("59.00", EMISION.plusDays(30)), cuota("59.00", EMISION.plusDays(60))));
        Comprobante c = factura(fp);
        assertThat(c.formaPago().cuotas()).hasSize(2);
        assertThat(FormaPago.idCuota(1)).isEqualTo("Cuota001");
        assertThat(FormaPago.idCuota(12)).isEqualTo("Cuota012");
    }

    @Test void creditoConPendienteMenorAlTotalEsValido() {
        // Pago parcial al momento de la emisión: solo el neto pendiente va en cuotas.
        factura(FormaPago.credito(new BigDecimal("50.00"), List.of(cuota("50.00", EMISION.plusDays(15)))));
    }

    @Test void contadoNoAdmiteCuotasNiPendiente_3252() {
        rechaza(() -> new FormaPago(FormaPago.Tipo.CONTADO, null, List.of(cuota("10.00", EMISION.plusDays(1)))), "3252");
        rechaza(() -> new FormaPago(FormaPago.Tipo.CONTADO, new BigDecimal("10.00"), List.of()), "3252");
    }

    @Test void creditoSinCuotas_3249() {
        rechaza(() -> FormaPago.credito(new BigDecimal("118.00"), List.of()), "3249");
    }

    @Test void creditoSinPendiente_3251() {
        rechaza(() -> FormaPago.credito(null, List.of(cuota("118.00", EMISION.plusDays(1)))), "3251");
    }

    @Test void pendienteNoPositivoOConMasDeDosDecimales_3250() {
        rechaza(() -> FormaPago.credito(new BigDecimal("0.00"), List.of(cuota("0.00", EMISION.plusDays(1)))), "3250");
        rechaza(() -> FormaPago.credito(new BigDecimal("118.001"), List.of(cuota("118.001", EMISION.plusDays(1)))), "3250");
    }

    @Test void cuotaSinMontoPositivo_3253() {
        rechaza(() -> FormaPago.credito(new BigDecimal("118.00"), List.of(cuota("-1.00", EMISION.plusDays(1)))), "3253");
    }

    @Test void cuotaSinFecha_3256() {
        rechaza(() -> FormaPago.credito(new BigDecimal("118.00"), List.of(new FormaPago.Cuota(new BigDecimal("118.00"), null))), "3256");
    }

    @Test void cuotasNoSumanElPendiente_3319() {
        rechaza(() -> FormaPago.credito(new BigDecimal("118.00"),
                List.of(cuota("59.00", EMISION.plusDays(30)), cuota("58.00", EMISION.plusDays(60)))), "3319");
    }

    @Test void pendienteMayorAlTotal_3265() {
        rechaza(() -> factura(FormaPago.credito(new BigDecimal("118.01"), List.of(cuota("118.01", EMISION.plusDays(1))))), "3265");
    }

    @Test void cuotaQueVenceElDiaDeEmisionOAntes_3267() {
        rechaza(() -> factura(FormaPago.credito(new BigDecimal("118.00"), List.of(cuota("118.00", EMISION)))), "3267");
        rechaza(() -> factura(FormaPago.credito(new BigDecimal("118.00"), List.of(cuota("118.00", EMISION.minusDays(1))))), "3267");
    }

    @Test void sinFormaPago_3244() {
        rechaza(() -> factura(null), "3244");
    }
}
