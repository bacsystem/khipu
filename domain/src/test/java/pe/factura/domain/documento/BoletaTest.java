package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Boleta de venta (03, #20): se envía sola con sendBill como una factura. Lo que cambia es el receptor (Boleta2_0: DNI, otros documentos o, hasta S/ 700, sin documento)
 * y que crédito, retención, anticipos y exportación todavía no se emiten en boleta.
 */
class BoletaTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    private final UUID tenant = UUID.randomUUID();
    private final LocalDate hoy = LocalDate.of(2026, 9, 13);
    private final Receptor dni = new Receptor("1", "12345678", "JUAN PEREZ", null);
    private final Receptor sinDocumento = new Receptor("-", "-", "CLIENTES VARIOS", null);

    private static List<Item> items(String precio) { return List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal(precio), TipoAfectacionIgv.GRAVADO)); }

    private Comprobante.FacturaBuilder boleta(Receptor r, String precio) { return Comprobante.boleta(tenant, "B001", hoy, "PEN", "0101", r, items(precio)); }

    @Test void unaBoletaConDniNaceRecibidaYEsTipo03() {
        Comprobante c = boleta(dni, "118.00").crear(clock);
        assertThat(c.tipo()).isEqualTo(TipoDocumento.BOLETA);
        assertThat(c.estado()).isEqualTo(EstadoDocumento.RECIBIDO);
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        c.asignarNumero(7, "20100066603");
        assertThat(c.nombreArchivo()).isEqualTo("20100066603-03-B001-7");
    }

    @Test void laSerieDeUnaBoletaEmpiezaConB() {
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "F001", hoy, "PEN", "0101", dni, items("10")).crear(clock))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void sinDocumentoSoloHastaSetecientosSoles() {
        assertThat(boleta(sinDocumento, "700.00").crear(clock).receptor().sinDocumento()).isTrue();
        assertThatThrownBy(() -> boleta(sinDocumento, "700.01").crear(clock))
                .isInstanceOf(DomainException.class).hasMessageContaining("700").extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        // En otra moneda no hay cómo comparar con S/ 700 sin tipo de cambio: se identifica al comprador.
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "USD", "0101", sinDocumento, items("10")).crear(clock))
                .extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1234567, JUAN PEREZ, 4207",
            "1, 1234567A, JUAN PEREZ, 4207",
            "6, 20601234560, CLIENTE SAC, 2017",
            "4, 'CE 123', JUAN PEREZ, 4208",
            "7, 1234567890123456, JUAN PEREZ, 4208",
            "-, 12345678, JUAN PEREZ, 2802",
            "X, 12345678, JUAN PEREZ, 2016",
            "1, 12345678, JP, 2022",
    })
    void elReceptorSigueLasReglasDeBoleta(String tipo, String numero, String nombre, String regla) {
        assertThatThrownBy(() -> boleta(new Receptor(tipo, numero, nombre, null), "10").crear(clock))
                .isInstanceOf(DomainException.class).hasMessageStartingWith(regla).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    @Test void admiteRucCarnetPasaporteYOtrosDelCatalogo06() {
        for (Receptor r : List.of(new Receptor("6", "20601234565", "CLIENTE SAC", null), new Receptor("4", "001234567", "ANA DIAZ", null),
                new Receptor("7", "AB1234567", "JOHN DOE", null), new Receptor("0", "X123", "JOHN DOE", null)))
            assertThat(boleta(r, "10").crear(clock).receptor()).isEqualTo(r);
    }

    @Test void sinReceptorEsUnError() {
        assertThatThrownBy(() -> boleta(null, "10").crear(clock)).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    /** Catálogo 51: 0113 (NRUS) es solo de boletas; 0112 (venta interna sustenta gastos deducibles PN) es solo de facturas. */
    @Test void elTipoDeOperacionDebeAplicarABoletas() {
        assertThat(Comprobante.boleta(tenant, "B001", hoy, "PEN", "0113", dni, items("10")).crear(clock).tipoOperacion()).isEqualTo("0113");
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "PEN", "0112", dni, items("10")).crear(clock))
                .hasMessageStartingWith("3206").hasMessageContaining("no aplica a boletas");
    }

    @Test void loQueTodaviaNoSeEmiteEnBoletaSeRechazaConClaridad() {
        assertThatThrownBy(() -> boleta(dni, "100").formaPago(FormaPago.credito(new BigDecimal("100"), List.of(new FormaPago.Cuota(new BigDecimal("100"), hoy.plusDays(30))))).crear(clock))
                .extracting("codigo").isEqualTo("FORMA_PAGO_INVALIDA");
        assertThatThrownBy(() -> boleta(dni, "1000").retencion(new RetencionIgv(null, null)).crear(clock))
                .extracting("codigo").isEqualTo("RETENCION_INVALIDA");
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy, "USD", "0200", new Receptor("7", "AB123", "JOHN DOE", null, "US"),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.EXPORTACION))).crear(clock))
                .extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
    }

    /** En boletas el rechazo por plazo vencido es 1079 (Boleta2_0), no el 2108 de las facturas. */
    @Test void fueraDePlazoCitaLaReglaDeBoleta() {
        assertThatThrownBy(() -> Comprobante.boleta(tenant, "B001", hoy.minusDays(4), "PEN", "0101", dni, items("10")).crear(clock))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("1079").extracting("codigo").isEqualTo("FECHA_INVALIDA");
    }
}
