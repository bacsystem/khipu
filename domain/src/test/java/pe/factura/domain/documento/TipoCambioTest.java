package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * #316: monedas del catálogo 02 (ISO 4217) y tipo de cambio por comprobante. El XML de SUNAT no lleva tipo de cambio: es un dato del comprobante para el registro de ventas
 * (SIRE), el PDF (equivalente en soles) y el tope de S/ 700 de una boleta sin documento emitida en otra moneda.
 */
class TipoCambioTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate HOY = LocalDate.of(2026, 9, 13);
    static final UUID TENANT = UUID.randomUUID();
    static final Receptor RUC = new Receptor("6", "20131312955", "SUNAT", "AV. GARCILASO DE LA VEGA 1472, LIMA");
    static final Receptor SIN_DOCUMENTO = new Receptor("-", "-", "CLIENTES VARIOS", null);

    static Item servicio(String precio) { return new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal(precio), TipoAfectacionIgv.GRAVADO); }

    static Comprobante.FacturaBuilder factura(String moneda, String precio) {
        return Comprobante.factura(TENANT, "F001", HOY, moneda, "0101", RUC, List.of(servicio(precio)));
    }

    @ParameterizedTest(name = "{0} es una moneda admitida")
    @ValueSource(strings = {"PEN", "USD", "EUR", "GBP", "CAD", "AUD", "CHF", "JPY", "CNY", "MXN", "BRL", "CLP", "COP", "ARS", "BOB", "UYU", "PYG"})
    void lasMonedasQueKhipuNombra(String moneda) {
        assertThat(Monedas.admitida(moneda)).isTrue();
        assertThat(Monedas.nombreEnLetras(moneda)).isNotBlank();
        assertThat(factura(moneda, "118.00").crear(CLOCK).moneda()).isEqualTo(moneda);
    }

    @ParameterizedTest(name = "{0} no es una moneda admitida")
    @ValueSource(strings = {"XXX", "ABC", "pen", "US", "USDD", ""})
    void lasDemasSeRechazanAntesDeNumerar(String moneda) {
        assertThat(Monedas.admitida(moneda)).isFalse();
        assertThatThrownBy(() -> factura(moneda, "118.00").crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("3088").extracting("codigo").isEqualTo("MONEDA_INVALIDA");
    }

    @Test void elMontoEnLetrasNombraLaMoneda() {
        assertThat(MontoEnLetras.de(new BigDecimal("118.50"), "GBP")).isEqualTo("CIENTO DIECIOCHO CON 50/100 LIBRAS ESTERLINAS");
        assertThat(MontoEnLetras.de(new BigDecimal("1000.00"), "USD")).isEqualTo("MIL CON 00/100 DOLARES AMERICANOS");
        assertThat(MontoEnLetras.de(new BigDecimal("10.00"), "PEN")).isEqualTo("DIEZ CON 00/100 SOLES");
    }

    @Test void elTipoDeCambioSeGuardaYDaElEquivalenteEnSoles() {
        Comprobante c = factura("USD", "118.00").tipoCambio(new BigDecimal("3.756")).crear(CLOCK);
        assertThat(c.tipoCambio()).isEqualByComparingTo("3.756");
        assertThat(c.totalEnSoles()).hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("443.21"));   // 118.00 × 3.756
        assertThat(factura("USD", "118.00").crear(CLOCK).tipoCambio()).isNull();
        assertThat(factura("USD", "118.00").crear(CLOCK).totalEnSoles()).isEmpty();
    }

    @Test void enSolesNoHayTipoDeCambio() {
        assertThatThrownBy(() -> factura("PEN", "118.00").tipoCambio(new BigDecimal("1")).crear(CLOCK))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TIPO_CAMBIO_INVALIDO");
    }

    @ParameterizedTest(name = "el tipo de cambio {0} no es válido")
    @ValueSource(strings = {"0", "-3.7", "1000", "3.7561"})
    void esPositivoDeTresEnterosYTresDecimales(String valor) {
        assertThatThrownBy(() -> factura("USD", "118.00").tipoCambio(new BigDecimal(valor)).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("tipo de cambio").extracting("codigo").isEqualTo("TIPO_CAMBIO_INVALIDO");
    }

    @Test void laBoletaEnDolaresSinDocumentoSeMideEnSolesConElTipoDeCambio() {
        // 100 USD × 3.75 = S/ 375 ≤ 700: se emite sin documento; 200 USD × 3.75 = S/ 750 > 700: pide documento.
        Comprobante c = Comprobante.boleta(TENANT, "B001", HOY, "USD", "0101", SIN_DOCUMENTO, List.of(servicio("100.00"))).tipoCambio(new BigDecimal("3.75")).crear(CLOCK);
        assertThat(c.receptor().sinDocumento()).isTrue();
        assertThat(c.totalEnSoles()).hasValueSatisfying(s -> assertThat(s).isEqualByComparingTo("375.00"));
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "USD", "0101", SIN_DOCUMENTO, List.of(servicio("200.00"))).tipoCambio(new BigDecimal("3.75")).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("700").extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        // Sin tipo de cambio no hay cómo medir los S/ 700: se sigue pidiendo el documento.
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "USD", "0101", SIN_DOCUMENTO, List.of(servicio("100.00"))).crear(CLOCK))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }
}
