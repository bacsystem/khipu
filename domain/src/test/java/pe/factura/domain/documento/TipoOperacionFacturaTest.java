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
 * #394, regla 3206: la factura solo emite los tipos de operación cuyos datos khipu arma en el XML. e-beta (2026-10-18) rechazó con el número ya
 * consumido el 0112 (3181), 0301 (3168), 0302 (3156), 2002 (3316), 2100–2102 (3241), 2104 (3242) y 2106 (2800); aceptó el 0401. El 2103 lo aceptó e-beta, pero es de RUC afiliados al SEE-Empresas supervisadas (1080, fila 437 de Factura2_0): no se emite.
 */
class TipoOperacionFacturaTest {
    static final LocalDate EMISION = LocalDate.of(2026, 9, 13);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor CLIENTE = new Receptor("6", "20131312955", "SUNAT", "AV. GARCILASO DE LA VEGA 1472, LIMA", null);

    static Comprobante.FacturaBuilder factura(String tipoOperacion) {
        Item servicio = new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO);
        return Comprobante.factura(UUID.randomUUID(), "F001", EMISION, "PEN", tipoOperacion, CLIENTE, List.of(servicio));
    }

    @ParameterizedTest(name = "{0} todavía no se emite en factura")
    @ValueSource(strings = {"0112", "0301", "0302", "2002", "2100", "2101", "2102", "2103", "2104", "2106"})
    void rechazaElTipoQueSunatRechazaDespuesDeNumerar(String operacion) {
        assertThatThrownBy(() -> factura(operacion).crear(CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("3206").hasMessageContaining(operacion).hasMessageContaining("todavía no se emite en factura")
                .extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
    }

    @Test void elMensajeDiceQueTiposSiSeEmiten() {
        assertThatThrownBy(() -> factura("0112").crear(CLOCK)).hasMessageContaining("0101, 0200").hasMessageContaining("0401");
    }

    @Test void sinTipoEsVentaInterna() {
        assertThat(factura(null).crear(CLOCK).tipoOperacion()).isEqualTo("0101");
    }

    @Test void siguenAceptadasLasOperacionesQueKhipuArma() {
        // 0401 lo aceptó e-beta sin datos extra; los demás (exportación, detracción, percepción) tienen sus propias pruebas con datos.
        for (String operacion : List.of("0101", "0401"))
            assertThat(factura(operacion).crear(CLOCK).tipoOperacion()).isEqualTo(operacion);
        assertThat(Comprobante.OPERACIONES_EN_FACTURA)
                .containsExactlyInAnyOrder("0101", "0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401", "1001", "1002", "1003", "1004", "2001");
    }

    @Test void hospedajeYPaqueteTuristicoConservanSuMensajeDelHuesped() {
        Receptor exterior = new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St, Miami FL", "US");
        Item cafe = new Item("CAF", "Café", "KGM", BigDecimal.ONE, new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION);
        for (String operacion : List.of("0202", "0205"))
            assertThatThrownBy(() -> Comprobante.factura(UUID.randomUUID(), "F001", EMISION, "USD", operacion, exterior, List.of(cafe)).crear(CLOCK))
                    .hasMessageContaining("huésped");
    }
}
