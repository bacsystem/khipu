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
    static final PlazoEnvio FACTURA = PlazoEnvio.de(TipoDocumento.FACTURA, null);
    static final PlazoEnvio BOLETA = PlazoEnvio.de(TipoDocumento.BOLETA, null);

    static PlazoEnvio deNota(TipoDocumento tipo, TipoDocumento afectado, String motivo) {
        return PlazoEnvio.de(tipo, new Nota(afectado, afectado == TipoDocumento.BOLETA ? "B001" : "F001", 1, motivo, "x"));
    }

    @Test void tresDiasCalendarioInclusive() {
        LocalDate emision = LocalDate.of(2026, 9, 10);   // jueves
        assertThat(FACTURA.fechaLimite(emision)).isEqualTo(LocalDate.of(2026, 9, 13));   // domingo, cuenta igual
        assertThat(FACTURA.vencido(emision, LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(FACTURA.vencido(emision, LocalDate.of(2026, 9, 14))).isTrue();
        assertThat(deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.FACTURA, "01").vencido(emision, LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.FACTURA, "01").vencido(emision, LocalDate.of(2026, 9, 14))).isTrue();
    }

    /**
     * #290: la boleta va sola (sendBill) hasta el 5.º día (RS 097-2012 arts. 7.3, 12 y 21, texto de la RS 114-2019); del 6.º al 7.º solo en un resumen diario.
     * La factura sigue con 3 días.
     */
    @Test void laBoletaVaSolaHastaElQuintoDiaYDelSextoAlSeptimoSoloEnResumen() {
        LocalDate emision = LocalDate.of(2026, 9, 10);
        assertThat(BOLETA.diasEnvioIndividual()).isEqualTo(5);
        assertThat(FACTURA.diasEnvioIndividual()).isEqualTo(3);
        assertThat(BOLETA.soloPorResumen(emision, LocalDate.of(2026, 9, 15))).as("5.º día: todavía sola").isFalse();
        assertThat(BOLETA.soloPorResumen(emision, LocalDate.of(2026, 9, 16))).as("6.º día: solo en resumen").isTrue();
        assertThat(BOLETA.soloPorResumen(emision, LocalDate.of(2026, 9, 17))).as("7.º día: solo en resumen").isTrue();
        assertThat(BOLETA.vencido(emision, LocalDate.of(2026, 9, 18))).as("8.º día: vencida").isTrue();
        assertThat(FACTURA.vencido(emision, LocalDate.of(2026, 9, 14))).as("la factura no cambia").isTrue();
    }

    /** #293: una nota B sigue a la boleta (NotaCredito2_0 filas 18–19: 1079 pasado el 5.º día); una nota F, a la factura (2108 al 4.º). */
    @Test void laNotaDeBoletaTieneElPlazoDeLaBoleta() {
        LocalDate emision = LocalDate.of(2026, 9, 10);
        PlazoEnvio nc = deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "07");
        assertThat(nc.soloPorResumen(emision, LocalDate.of(2026, 9, 15))).as("5.º día: todavía sola").isFalse();
        assertThat(nc.soloPorResumen(emision, LocalDate.of(2026, 9, 16))).as("6.º día: solo en resumen").isTrue();
        assertThat(nc.vencido(emision, LocalDate.of(2026, 9, 17))).as("7.º día: en plazo").isFalse();
        assertThat(nc.vencido(emision, LocalDate.of(2026, 9, 18))).as("8.º día: vencida").isTrue();
        assertThat(nc.motivoDeRechazo(emision)).startsWith("1079").contains("nota").contains("2026-09-17");
        assertThat(deNota(TipoDocumento.NOTA_DEBITO, TipoDocumento.BOLETA, "01").fechaLimite(emision)).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.FACTURA, "01").motivoDeRechazo(emision)).startsWith("2108");
    }

    /** La NC 10 y la ND 03 no van en el resumen diario (guía, p. 19): pasado el envío individual no les queda canal, así que su plazo final es el 5.º día. */
    @Test void laNotaDeBoletaQueNoVaEnResumenVenceAlQuintoDia() {
        LocalDate emision = LocalDate.of(2026, 9, 10);
        for (PlazoEnvio p : List.of(deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "10"), deNota(TipoDocumento.NOTA_DEBITO, TipoDocumento.BOLETA, "03"))) {
            assertThat(p.vencido(emision, LocalDate.of(2026, 9, 15))).as("5.º día: en plazo").isFalse();
            assertThat(p.vencido(emision, LocalDate.of(2026, 9, 16))).as("6.º día: vencida").isTrue();
            assertThat(p.soloPorResumen(emision, LocalDate.of(2026, 9, 16))).isFalse();
            assertThat(p.motivoDeRechazo(emision)).startsWith("1079").contains("no se informa en el resumen diario").contains("2026-09-15");
        }
    }

    /**
     * #331: pasado el 7.º día SUNAT no rechaza el alta en el resumen diario: la observa (4443, Resumen Diario 1_1 fila 14, vigente desde 2024-11-11). La boleta y la
     * nota B que va en él se siguen informando, fuera de plazo; nunca se quedan sin canal. La factura, sus notas y la NC 10 / ND 03 de boleta sí (2108, 1079).
     */
    @Test void pasadoElSeptimoDiaLaBoletaSeSigueInformandoEnElResumenFueraDePlazo() {
        LocalDate emision = LocalDate.of(2026, 9, 10);
        for (PlazoEnvio p : List.of(BOLETA, deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "07"), deNota(TipoDocumento.NOTA_DEBITO, TipoDocumento.BOLETA, "01"))) {
            assertThat(p.soloPorResumen(emision, LocalDate.of(2026, 9, 18))).as("8.º día: en el resumen").isTrue();
            assertThat(p.soloPorResumen(emision, LocalDate.of(2026, 10, 30))).as("semanas después: en el resumen").isTrue();
            assertThat(p.informaFueraDePlazo(emision, LocalDate.of(2026, 9, 17))).as("7.º día: en plazo").isFalse();
            assertThat(p.informaFueraDePlazo(emision, LocalDate.of(2026, 9, 18))).as("8.º día: fuera de plazo, con 4443").isTrue();
            assertThat(p.sinCanal(emision, LocalDate.of(2026, 10, 30))).isFalse();
        }
        assertThat(FACTURA.sinCanal(emision, LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(FACTURA.sinCanal(emision, LocalDate.of(2026, 9, 14))).as("2108").isTrue();
        assertThat(FACTURA.informaFueraDePlazo(emision, LocalDate.of(2026, 9, 14))).isFalse();
        assertThat(FACTURA.soloPorResumen(emision, LocalDate.of(2026, 9, 14))).isFalse();
        PlazoEnvio nc10 = deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "10");
        assertThat(nc10.sinCanal(emision, LocalDate.of(2026, 9, 16))).as("la NC 10 no va en el resumen: sin canal al 6.º día").isTrue();
        assertThat(nc10.informaFueraDePlazo(emision, LocalDate.of(2026, 9, 18))).isFalse();
    }

    /** El corte grueso de ControlarPlazoEnvioService usa este mínimo: debe seguir siendo válido si algún tipo cambia su plazo. */
    @Test void diasMinimoEsElMenorEntreTodosLosPlazos() {
        assertThat(PlazoEnvio.diasMinimo()).isEqualTo(3);
        for (PlazoEnvio p : List.of(FACTURA, BOLETA, deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "07"), deNota(TipoDocumento.NOTA_CREDITO, TipoDocumento.BOLETA, "10")))
            assertThat(PlazoEnvio.diasMinimo()).isLessThanOrEqualTo(p.dias());
    }

    @Test void finDeMesYAnioBisiesto() {
        assertThat(FACTURA.fechaLimite(LocalDate.of(2026, 1, 30))).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(FACTURA.fechaLimite(LocalDate.of(2028, 2, 27))).isEqualTo(LocalDate.of(2028, 3, 1));   // 29 de febrero cuenta
        assertThat(deNota(TipoDocumento.NOTA_DEBITO, TipoDocumento.FACTURA, "01").fechaLimite(LocalDate.of(2026, 12, 30))).isEqualTo(LocalDate.of(2027, 1, 2));
    }

    @Test void elComprobanteConoceSuLimiteYSoloSeMarcaVencidoCuandoLoEsta() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 10), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(reloj);
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
        assertThat(Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 10), "PEN", "0101", r, items).crear(reloj).fechaEmision()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 9), "PEN", "0101", r, items).crear(reloj))
                .isInstanceOf(DomainException.class).hasMessageContaining("2108").hasMessageContaining("2026-09-12");
    }

    /** #293: una nota de boleta del 6.º día se emite (irá en el resumen diario), salvo la NC 10 o la ND 03, que no tienen canal. */
    @Test void laNotaDeBoletaDelSextoDiaSoloSeEmiteSiPuedeIrEnElResumen() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-16T15:00:00Z"), ZoneId.of("America/Lima"));
        Receptor r = new Receptor("-", "-", "CLIENTES VARIOS", null);
        List<Item> items = List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO));
        LocalDate emision = LocalDate.of(2026, 9, 10);
        Comprobante nc07 = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "BC01", emision, new Nota(TipoDocumento.BOLETA, "B001", 1, "07", "x"), r, items).crear(reloj);
        assertThat(nc07.soloPorResumen(LocalDate.of(2026, 9, 16))).isTrue();
        assertThatThrownBy(() -> Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "BC01", emision, new Nota(TipoDocumento.BOLETA, "B001", 1, "10", "x"), r, items).crear(reloj))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("FECHA_INVALIDA");
    }
}
