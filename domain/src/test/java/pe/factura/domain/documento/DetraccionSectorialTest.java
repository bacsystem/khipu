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

    /** Las reglas de campo se exigen con {@link Hidrobiologico#exigirValido()}, no al construir el record (#89: no revalidar al rehidratar). */
    @Test void hidrobiologicoValidaSusCampos() {
        assertThatThrownBy(() -> new Hidrobiologico(null, "N", "E", "L", LocalDate.now(), BigDecimal.ONE).exigirValido()).hasMessageContaining("3063");
        assertThatThrownBy(() -> new Hidrobiologico("M", "", "E", "L", LocalDate.now(), BigDecimal.ONE).exigirValido()).hasMessageContaining("3130");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", " ", "L", LocalDate.now(), BigDecimal.ONE).exigirValido()).hasMessageContaining("3131");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", null, LocalDate.now(), BigDecimal.ONE).exigirValido()).hasMessageContaining("3132");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", null, BigDecimal.ONE).exigirValido()).hasMessageContaining("3134");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", LocalDate.now(), null).exigirValido()).hasMessageContaining("3133");
        assertThatThrownBy(() -> new Hidrobiologico("M", "N", "E", "L", LocalDate.now(), new BigDecimal("1.234")).exigirValido()).hasMessageContaining("4281");
        assertThatThrownBy(() -> new Hidrobiologico("MATRICULA-DEMASIADO-LARGA", "N", "E", "L", LocalDate.now(), BigDecimal.ONE).exigirValido()).hasMessageContaining("4280").hasMessageContaining("15");
        // Construir sin llamar exigirValido() nunca lanza: la normalización (strip) es la única regla que corre siempre.
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

    /**
     * Las reglas de {@code TransporteCarga} (incluidas las de sus records anidados: {@code Punto} contra el catálogo 13,
     * {@code ValorReferencial}) se exigen con {@link TransporteCarga#exigirValido()}, no al construir los records.
     */
    @Test void transporteValidaSusCampos() {
        TransporteCarga.ValorReferencial vr = FLETE.valorReferencial();
        assertThatThrownBy(() -> new TransporteCarga(null, LIMA, "Detalle", vr, null).exigirValido()).hasMessageContaining("3116");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, null, "Detalle", vr, null).exigirValido()).hasMessageContaining("3118");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "ab", vr, null).exigirValido()).hasMessageContaining("3120");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle", null, null).exigirValido()).hasMessageContaining("3122");
        // Punto con ubigeo fuera del catálogo 13 o dirección corta: no lanzan al construirse, solo al validar el transporte completo.
        TransporteCarga.Punto ubigeoInvalido = new TransporteCarga.Punto("999999", "Dirección");
        assertThatThrownBy(() -> new TransporteCarga(ubigeoInvalido, LIMA, "Detalle", vr, null).exigirValido()).hasMessageContaining("3116").hasMessageContaining("catálogo 13");
        TransporteCarga.Punto direccionCorta = new TransporteCarga.Punto("150101", "ab");
        assertThatThrownBy(() -> new TransporteCarga(direccionCorta, LIMA, "Detalle", vr, null).exigirValido()).hasMessageContaining("3117");
        // ValorReferencial con un monto ausente o inválido: mismo patrón, solo lanza al validar el transporte completo.
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle", new TransporteCarga.ValorReferencial(null, BigDecimal.ONE, BigDecimal.ONE), null).exigirValido()).hasMessageContaining("3124");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle", new TransporteCarga.ValorReferencial(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE), null).exigirValido()).hasMessageContaining("3125");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle", new TransporteCarga.ValorReferencial(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1.234")), null).exigirValido()).hasMessageContaining("3126");
        // Un transporte completo y válido no lanza.
        FLETE.exigirValido();
    }

    @Test void tramosYVehiculosOpcionales() {
        TransporteCarga.Vehiculo camion = new TransporteCarga.Vehiculo("T3S3", new BigDecimal("30"), new BigDecimal("20"));
        TransporteCarga.Tramo tramo = new TransporteCarga.Tramo("021801", "150101", "Chimbote – Lima", new BigDecimal("2400"), null, List.of(camion));
        TransporteCarga t = new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(), List.of(tramo));
        assertThat(t.tramos()).hasSize(1);
        assertThat(t.tramos().get(0).vehiculos().get(0).cargaUtilTm()).isEqualByComparingTo("30.00");
        t.exigirValido();
        assertThat(new TransporteCarga.Tramo(null, null, null, null, null, null).vehiculos()).isEmpty();
        // Tramos y vehículos inválidos: no lanzan al construirse, solo al validar el transporte que los contiene.
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(),
                List.of(new TransporteCarga.Tramo("000000", null, null, null, null, null))).exigirValido()).hasMessageContaining("4200");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(),
                List.of(new TransporteCarga.Tramo(null, null, "ab", null, null, null))).exigirValido()).hasMessageContaining("4271");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(),
                List.of(new TransporteCarga.Tramo(null, null, null, null, null, List.of(new TransporteCarga.Vehiculo("T3 S3", null, null))))).exigirValido()).hasMessageContaining("4273");
        assertThatThrownBy(() -> new TransporteCarga(CHIMBOTE, LIMA, "Detalle del viaje", FLETE.valorReferencial(),
                List.of(new TransporteCarga.Tramo(null, null, null, null, null, List.of(new TransporteCarga.Vehiculo("T3S3", new BigDecimal("-1"), null))))).exigirValido()).hasMessageContaining("4276");
    }

    /** #89: un ubigeo del catálogo 13 puede reorganizarse después de emitido; eso no debe impedir leer un comprobante ya persistido. */
    @Test void noSeRevalidaAlRehidratar() {
        TransporteCarga.Punto ubigeoQueYaNoExisteEnElCatalogo = new TransporteCarga.Punto("999999", "Dirección");
        TransporteCarga transporteInvalido = new TransporteCarga(ubigeoQueYaNoExisteEnElCatalogo, LIMA, "Detalle del viaje", FLETE.valorReferencial(), null);
        assertThatThrownBy(transporteInvalido::exigirValido).hasMessageContaining("catálogo 13");

        Comprobante rehidratado = Comprobante.persistido(UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), EstadoDocumento.ACEPTADO,
                        RECEPTOR, List.of(item(null, transporteInvalido)))
                .tipoOperacion("1004").firma("h", "n", "k").rehidratar();
        assertThat(rehidratado.items().get(0).transporte()).isEqualTo(transporteInvalido);
    }
}
