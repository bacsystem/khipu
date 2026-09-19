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

/**
 * Datos sectoriales de la detracción (#69): 1002 recursos hidrobiológicos (campos 107–112, conceptos 3001–3006 del catálogo 55)
 * y 1004 transporte de carga (campos 113–127): obligatorios en cada ítem de esas operaciones y prohibidos fuera de ellas.
 */
class DetraccionSectorialTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE SAC", null);
    static final Hidrobiologico ANCHOVETA = new Hidrobiologico("CO-12345-PM", "DON JOSÉ II", "Anchoveta (Engraulis ringens)", "Muelle de Chimbote", LocalDate.of(2026, 9, 10), new BigDecimal("12.5"));
    static final TransporteCarga.Punto CHIMBOTE = new TransporteCarga.Punto("021801", "Av. Los Pescadores 450, Chimbote");
    static final TransporteCarga.Punto LIMA = new TransporteCarga.Punto("150101", "Jr. de la Unión 100, Lima");
    static final TransporteCarga FLETE = new TransporteCarga(CHIMBOTE, LIMA, "Traslado de 20 t de harina de pescado", new TransporteCarga.ValorReferencial(new BigDecimal("2500"), new BigDecimal("2400"), new BigDecimal("2600")), null);

    static Item item(Hidrobiologico h, TransporteCarga t) {
        return new Item("P1", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), null, null, h, t);
    }

    static Comprobante factura(String tipoOperacion, Item item) {
        return Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", tipoOperacion, RECEPTOR, List.of(item))
                .detraccion(new Detraccion(switch (tipoOperacion) { case "1002" -> "004"; case "1004" -> "027"; default -> "022"; }, new BigDecimal("4"), null, "00-000-123456", null)).crear(CLOCK);
    }

    @Test void hidrobiologicoObligatorioEn1002() {
        Comprobante c = factura("1002", item(ANCHOVETA, null));
        assertThat(c.items().get(0).hidrobiologico()).isEqualTo(ANCHOVETA);
        assertThat(c.items().get(0).hidrobiologico().cantidad()).isEqualByComparingTo("12.5");
        assertThatThrownBy(() -> factura("1002", item(null, null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("3063").extracting("codigo").isEqualTo("DETRACCION_INVALIDA");
        // Fuera de 1002 no se admite
        assertThatThrownBy(() -> factura("1001", item(ANCHOVETA, null))).hasMessageContaining("solo aplican al tipo de operación 1002");
    }

    @Test void hidrobiologicoValidaSusCampos() {
        assertThatThrownBy(() -> new Hidrobiologico(null, "N", "E", "L", LocalDate.now(), BigDecimal.ONE)).hasMessageContaining("3063");
        assertThatThrownBy(() -> new Hidrobiologico("M", "", "E", "L", LocalDate.now(), BigDecimal.ONE)).hasMessageContaining("3130");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", " ", "L", LocalDate.now(), BigDecimal.ONE)).hasMessageContaining("3131");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", null, LocalDate.now(), BigDecimal.ONE)).hasMessageContaining("3132");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", null, BigDecimal.ONE)).hasMessageContaining("3134");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", LocalDate.now(), null)).hasMessageContaining("3133");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", LocalDate.now(), new BigDecimal("1.234"))).hasMessageContaining("4281");
        assertThatThrownBy(() -> new Hidrobiologico("MATRICULA-DEMASIADO-LARGA", "N", "E", "L", LocalDate.now(), BigDecimal.ONE)).hasMessageContaining("4280").hasMessageContaining("15");
        assertThat(new Hidrobiologico(" M ", "N", "E", "L", LocalDate.now(), BigDecimal.ONE).matricula()).isEqualTo("M");
    }

    @Test void transporteObligatorioEn1004() {
        Comprobante c = factura("1004", item(null, FLETE));
        assertThat(c.items().get(0).transporte().origen()).isEqualTo(CHIMBOTE);
        assertThat(c.items().get(0).transporte().valorReferencial().servicio()).isEqualByComparingTo("2500.00");
        assertThatThrownBy(() -> factura("1004", item(null, null)))
                .isInstanceOf(DomainException.class).hasMessageContaining("3116").extracting("codigo").isEqualTo("DETRACCION_INVALIDA");
        assertThatThrownBy(() -> factura("1001", item(null, FLETE))).hasMessageContaining("solo aplican al tipo de operación 1004");
        // Cada ítem: uno con datos y otro sin ellos no pasa
        assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "1004", RECEPTOR, List.of(item(null, FLETE), item(null, null)))
                .detraccion(new Detraccion("027", new BigDecimal("4"), null, "00-000-123456", null)).crear(CLOCK)).hasMessageContaining("3116");
    }

    @Test void transporteValidaSusCampos() {
        TransporteCarga.ValorReferencial vr = FLETE.valorReferencial();
        assertThatThrownBy(() -> new TransporteCarga(null, LIMA, "Detalle", vr, null)).hasMessageContaining("3116");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, null, "Detalle", vr, null)).hasMessageContaining("3118");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "ab", vr, null)).hasMessageContaining("3120");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle", null, null)).hasMessageContaining("3122");
        assertThatThrownBy(() -> new TransporteCarga.Punto("999999", "Dirección")).hasMessageContaining("3116").hasMessageContaining("catálogo 13");
        assertThatThrownBy(() -> new TransporteCarga.Punto("150101", "ab")).hasMessageContaining("3117");
        assertThatThrownBy(() -> new TransporteCarga.ValorReferencial(null, BigDecimal.ONE, BigDecimal.ONE)).hasMessageContaining("3124");
        assertThatThrownBy(() -> new TransporteCarga.ValorReferencial(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)).hasMessageContaining("3125");
        assertThatThrownBy(() -> new TransporteCarga.ValorReferencial(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1.234"))).hasMessageContaining("3126");
        assertThat(new TransporteCarga.ValorReferencial(new BigDecimal("2500"), BigDecimal.ONE, BigDecimal.ONE).servicio()).isEqualTo(new BigDecimal("2500.00"));
    }

    @Test void tramosYVehiculosOpcionales() {
        TransporteCarga.Vehiculo camion = new TransporteCarga.Vehiculo("T3S3", new BigDecimal("30"), new BigDecimal("20"));
        TransporteCarga.Tramo tramo = new TransporteCarga.Tramo("021801", "150101", "Chimbote – Lima", new BigDecimal("2400"), null, List.of(camion));
        TransporteCarga t = new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(), List.of(tramo));
        assertThat(t.tramos()).hasSize(1);
        assertThat(t.tramos().get(0).vehiculos().get(0).cargaUtilTm()).isEqualByComparingTo("30.00");
        assertThat(new TransporteCarga.Tramo(null, null, null, null, null, null).vehiculos()).isEmpty();
        assertThatThrownBy(() -> new TransporteCarga.Tramo("000000", null, null, null, null, null)).hasMessageContaining("4200");
        assertThatThrownBy(() -> new TransporteCarga.Tramo(null, null, "ab", null, null, null)).hasMessageContaining("4271");
        assertThatThrownBy(() -> new TransporteCarga.Vehiculo("T3 S3", null, null)).hasMessageContaining("4273");
        assertThatThrownBy(() -> new TransporteCarga.Vehiculo("T3S3", new BigDecimal("-1"), null)).hasMessageContaining("4276");
    }
}
