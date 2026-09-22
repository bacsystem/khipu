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

/** RS 193-2020: hasta el 3.er día calendario desde el día siguiente a la emisión; feriados y fines de semana cuentan. */
class PlazoEnvioTest {

    @Test void tresDiasCalendarioInclusive() {
        LocalDate emision = LocalDate.of(2026, 9, 10);   // jueves
        assertThat(PlazoEnvio.fechaLimite(TipoDocumento.FACTURA, emision)).isEqualTo(LocalDate.of(2026, 9, 13));   // domingo, cuenta igual
        assertThat(PlazoEnvio.vencido(TipoDocumento.FACTURA, emision, LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(PlazoEnvio.vencido(TipoDocumento.FACTURA, emision, LocalDate.of(2026, 9, 14))).isTrue();
        assertThat(PlazoEnvio.vencido(TipoDocumento.NOTA_CREDITO, emision, LocalDate.of(2026, 9, 13))).isFalse();
    }

    /** El corte grueso de ControlarPlazoEnvioService usa este mínimo: debe seguir siendo válido si algún tipo cambia su plazo. */
    @Test void diasMinimoEsElMenorEntreTodosLosTipos() {
        assertThat(PlazoEnvio.diasMinimo()).isEqualTo(3);
        for (TipoDocumento t : TipoDocumento.values()) assertThat(PlazoEnvio.diasMinimo()).isLessThanOrEqualTo(PlazoEnvio.dias(t));
    }

    @Test void finDeMesYAnioBisiesto() {
        assertThat(PlazoEnvio.fechaLimite(TipoDocumento.FACTURA, LocalDate.of(2026, 1, 30))).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(PlazoEnvio.fechaLimite(TipoDocumento.FACTURA, LocalDate.of(2028, 2, 27))).isEqualTo(LocalDate.of(2028, 3, 1));   // 29 de febrero cuenta
        assertThat(PlazoEnvio.fechaLimite(TipoDocumento.NOTA_DEBITO, LocalDate.of(2026, 12, 30))).isEqualTo(LocalDate.of(2027, 1, 2));
    }

    @Test void elComprobanteConoceSuLimiteYSoloSeMarcaVencidoCuandoLoEsta() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 10), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), reloj);
        c.asignarNumero(1, "20100066603");
        c.firmar("H", "k.xml");
        assertThat(c.fechaLimiteEnvio()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThatThrownBy(() -> c.marcarFueraDePlazo(LocalDate.of(2026, 9, 13))).isInstanceOf(DomainException.class).hasMessageContaining("todavía se puede enviar");
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FIRMADO);
        c.marcarFueraDePlazo(LocalDate.of(2026, 9, 14));
        assertThat(c.estado()).isEqualTo(EstadoDocumento.FUERA_DE_PLAZO);
        assertThat(c.ultimoError()).startsWith("2108").contains("2026-09-13");
        assertThat(c.estado().esEnviable()).isFalse();
        assertThatThrownBy(c::marcarEnviado).extracting("codigo").isEqualTo("TRANSICION_INVALIDA");
    }

    @Test void noSeEmiteConUnaFechaCuyoPlazoYaVencio() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
        Receptor r = new Receptor("6", "20601234565", "CLIENTE SAC", null);
        List<Item> items = List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO));
        assertThat(Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 10), "PEN", "0101", r, items, reloj).fechaEmision()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThatThrownBy(() -> Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 9), "PEN", "0101", r, items, reloj))
                .isInstanceOf(DomainException.class).hasMessageContaining("2108").hasMessageContaining("2026-09-12");
    }
}
