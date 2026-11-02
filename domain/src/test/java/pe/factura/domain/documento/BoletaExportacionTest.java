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
 * #296: la boleta de exportación (0200, 0201, 0203, 0204, 0206, 0207, 0208) y a no domiciliados (0401). e-beta (2026-10-18): acepta al comprador con pasaporte, con DNI y
 * sin documento (0200 y 0401); rechaza con 2800 al comprador con RUC, que Boleta2_0 (fila 93) prohíbe en esos tipos. 0202/0205 piden el huésped; 0301, 0302 y 2100–2105
 * todavía no tienen sus datos.
 */
class BoletaExportacionTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate HOY = LocalDate.of(2026, 9, 13);
    static final UUID TENANT = UUID.randomUUID();
    static final Receptor PASAPORTE = new Receptor("7", "US123456", "JOHN SMITH", null, "US");
    static final Receptor RUC = new Receptor("6", "20131312955", "SUNAT", null, "US");
    static final Item BIEN = new Item("CAF", "Café", "KGM", new BigDecimal("2"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION);
    static final Item SERVICIO_GRAVADO = new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO);

    static Comprobante.FacturaBuilder boleta(String operacion, Receptor comprador) {
        boolean exportacion = Exportacion.es(operacion);
        Comprobante.FacturaBuilder b = Comprobante.boleta(TENANT, "B001", HOY, exportacion ? "USD" : "PEN", operacion, comprador, List.of(exportacion ? BIEN : SERVICIO_GRAVADO));
        // 0201 y 0208 son servicios: exigen el país de uso (3098).
        return Exportacion.EXIGEN_PAIS_USO.contains(operacion) ? b.exportacion(new Exportacion(null, "US")) : b;
    }

    @ParameterizedTest(name = "{0} se emite en boleta")
    @ValueSource(strings = {"0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401"})
    void seEmiteConUnCompradorSinRuc(String operacion) {
        Comprobante c = boleta(operacion, PASAPORTE).crear(CLOCK);
        assertThat(c.tipoOperacion()).isEqualTo(operacion);
        assertThat(c.tipo()).isEqualTo(TipoDocumento.BOLETA);
    }

    @ParameterizedTest(name = "{0} con un comprador con RUC se rechaza (2800)")
    @ValueSource(strings = {"0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401"})
    void elCompradorConRucSeRechazaAntesDeNumerar(String operacion) {
        assertThatThrownBy(() -> boleta(operacion, RUC).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageStartingWith("2800").hasMessageContaining(operacion).extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    @Test void elDniYElCompradorSinDocumentoTambienVan() {
        assertThat(boleta("0200", new Receptor("1", "12345678", "JUAN PEREZ", null, "US")).crear(CLOCK).receptor().tipoDoc()).isEqualTo("1");
        // Sin documento: en soles y hasta S/ 700 (e-beta aceptó 0200 y 0401 así).
        Comprobante sinDocumento = Comprobante.boleta(TENANT, "B001", HOY, "PEN", "0200", new Receptor("-", "-", "CLIENTES VARIOS", null, "US"), List.of(BIEN)).crear(CLOCK);
        assertThat(sinDocumento.receptor().sinDocumento()).isTrue();
        assertThat(boleta("0401", new Receptor("-", "-", "CLIENTES VARIOS", null)).crear(CLOCK).tipoOperacion()).isEqualTo("0401");
    }

    @ParameterizedTest(name = "{0} todavía no se emite en boleta")
    @ValueSource(strings = {"0202", "0205", "0301", "0302", "2100", "2101", "2102", "2103", "2104", "2105"})
    void lasQueNoTienenSusDatosSiguenRechazadas(String operacion) {
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "PEN", operacion, PASAPORTE, List.of(SERVICIO_GRAVADO)).crear(CLOCK))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
    }

    @Test void laExportacionLlevaAfectacion40YLasDemasNo() {
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "USD", "0200", PASAPORTE, List.of(SERVICIO_GRAVADO)).crear(CLOCK))
                .hasMessageContaining("2642").extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "PEN", "0401", PASAPORTE, List.of(BIEN)).crear(CLOCK))
                .hasMessageContaining("3107").extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
    }

    @Test void elServicioExigeElPaisDeUso() {
        assertThatThrownBy(() -> Comprobante.boleta(TENANT, "B001", HOY, "USD", "0201", PASAPORTE, List.of(BIEN)).crear(CLOCK))
                .hasMessageContaining("3098").extracting("codigo").isEqualTo("EXPORTACION_INVALIDA");
    }

    @Test void laListaDeLaBoletaEsLaAcordada() {
        assertThat(Comprobante.OPERACIONES_EN_BOLETA).containsExactlyInAnyOrder(
                "0101", "0113", "0200", "0201", "0203", "0204", "0206", "0207", "0208", "0401", "1001", "1002", "1003", "1004", "2001");
    }
}
