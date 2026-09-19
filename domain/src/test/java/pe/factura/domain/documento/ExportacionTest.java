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
 * Factura de exportación (#65): tipos de operación 0200–0208 del catálogo 51, afectación 40 → tributo 9995 sin IGV (3110),
 * todas las líneas 40 (2642) y ninguna fuera de esos tipos (3107); receptor del exterior (2800–2802) con país; Incoterm y,
 * en servicios 0201/0208, país de uso (3098/3099).
 */
class ExportacionTest {
    static final LocalDate EMISION = LocalDate.of(2026, 9, 13);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor EXTERIOR = new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St, Miami FL", "US");

    static Item cafe(String cant) { return new Item("CAF", "Café verde en grano", "KGM", new BigDecimal(cant), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION); }

    static Comprobante.FacturaBuilder factura(String tipoOperacion, Receptor receptor, List<Item> items) {
        return Comprobante.factura(UUID.randomUUID(), "F001", EMISION, "USD", tipoOperacion, receptor, items);
    }

    @Test void lineaDeExportacionSinIgv() {
        ItemCalculado ic = ItemCalculado.de(cafe("1000"));
        assertThat(ic.tributo()).isEqualTo(Tributo.EXP);
        assertThat(ic.valorUnitario()).isEqualByComparingTo("4.5000000000");   // el precio no lleva IGV
        assertThat(ic.valorVenta()).isEqualByComparingTo("4500.00");
        assertThat(ic.igv()).isEqualByComparingTo("0.00");                     // 3110
        assertThat(ic.porcentajeIgv()).isEqualByComparingTo("0.00");
        assertThat(ic.precioVenta()).isEqualByComparingTo("4500.00");
    }

    @Test void totalesDeExportacion() {
        Totales t = Totales.calcular(List.of(cafe("1000"), cafe("500")));
        assertThat(t.subtotales()).extracting(Totales.SubtotalTributo::tributo).containsExactly(Tributo.EXP);
        assertThat(t.subtotales().get(0).base()).isEqualByComparingTo("6750.00");     // 3273: Σ LineExtensionAmount
        assertThat(t.subtotales().get(0).impuesto()).isEqualByComparingTo("0.00");    // 3000
        assertThat(t.exportacion()).isEqualByComparingTo("6750.00");
        assertThat(t.esExportacion()).isTrue();
        assertThat(t.gravado()).isEqualByComparingTo("0.00");
        assertThat(t.igv()).isEqualByComparingTo("0.00");
        assertThat(t.totalValorVenta()).isEqualByComparingTo("6750.00");               // 3278 incluye 9995
        assertThat(t.totalPrecioVenta()).isEqualByComparingTo("6750.00");
        assertThat(t.total()).isEqualByComparingTo("6750.00");
    }

    @Test void descuentoGlobal03SobreLaBaseDeExportacion() {
        Totales t = Totales.calcular(List.of(cafe("1000")), Descuento.porcentaje(BigDecimal.TEN, false), BigDecimal.ZERO);
        assertThat(t.descuentoGlobal().base()).isEqualByComparingTo("4500.00");
        assertThat(t.totalDescuentos()).isEqualByComparingTo("450.00");
        assertThat(t.total()).isEqualByComparingTo("4050.00");
        // Un descuento 02 (afecta la base del IGV) no tiene base en una exportación
        assertThatThrownBy(() -> Totales.calcular(List.of(cafe("1000")), Descuento.porcentaje(BigDecimal.TEN, true), BigDecimal.ZERO))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DESCUENTO_INVALIDO");
    }

    @Test void noSeMezclaConOtrasAfectacionesNiLlevaIscOIcbper() {
        assertThatThrownBy(() -> Totales.calcular(List.of(cafe("1000"), new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))))
                .isInstanceOf(DomainException.class).hasMessageContaining("3107");
        assertThatThrownBy(() -> ItemCalculado.de(new Item("C", "Café", "KGM", BigDecimal.ONE, new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION, null, new Isc("01", BigDecimal.TEN, null), false)))
                .hasMessageContaining("3223");
        assertThatThrownBy(() -> ItemCalculado.de(new Item("C", "Bolsa", "NIU", BigDecimal.ONE, new BigDecimal("0.50"), TipoAfectacionIgv.EXPORTACION, null, null, true)))
                .hasMessageContaining("3223");
    }

    @Test void facturaDeExportacionDeBienes() {
        Comprobante c = factura("0200", EXTERIOR, List.of(cafe("1000"))).exportacion(new Exportacion("FOB", null)).crear(CLOCK);
        assertThat(c.tipoOperacion()).isEqualTo("0200");
        assertThat(c.exportacion().incoterm()).isEqualTo("FOB");
        assertThat(c.receptor().pais()).isEqualTo("US");
        assertThat(c.totales().exportacion()).isEqualByComparingTo("4500.00");
        // Sin datos de exportación también vale: el Incoterm es opcional para SUNAT
        assertThat(factura("0200", EXTERIOR, List.of(cafe("1"))).crear(CLOCK).exportacion()).isNull();
    }

    @Test void afectacionYTipoDeOperacionVanJuntos() {
        // Exportación con ítem gravado (2642)
        assertThatThrownBy(() -> factura("0200", EXTERIOR, List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("2642").extracting("codigo").isEqualTo("AFECTACION_INVALIDA");
        // Venta interna con ítem 40 (3107)
        assertThatThrownBy(() -> factura("0101", new Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(cafe("1"))).crear(CLOCK))
                .hasMessageContaining("3107");
        // Datos de exportación en una venta interna
        assertThatThrownBy(() -> factura("0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).exportacion(new Exportacion("FOB", null)).crear(CLOCK))
                .extracting("codigo").isEqualTo("EXPORTACION_INVALIDA");
    }

    @Test void receptorDelExterior_2800() {
        // RUC en 0200 sin leyenda 2008
        assertThatThrownBy(() -> factura("0200", new Receptor("6", "20601234565", "CLIENTE SAC", null, "PE"), List.of(cafe("1"))).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("2800").extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
        // RUC en 0206 (servicios complementarios al transporte de carga) sí se admite
        assertThat(factura("0206", new Receptor("6", "20601234565", "NAVIERA SAC", null, "PE"), List.of(cafe("1"))).crear(CLOCK).receptor().esRuc()).isTrue();
        // Tipo de documento fuera del catálogo
        assertThatThrownBy(() -> factura("0200", new Receptor("9", "X", "ACME", null, "US"), List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("2800");
        // DNI mal formado (2801) y documento con espacios (2802)
        assertThatThrownBy(() -> factura("0200", new Receptor("1", "1234", "JUAN PEREZ", null, "US"), List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("2801");
        assertThatThrownBy(() -> factura("0200", new Receptor("0", "US 123", "ACME", null, "US"), List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("2802");
        // Sin país o con país inválido
        assertThatThrownBy(() -> factura("0200", new Receptor("0", "US123", "ACME", null, null), List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("país");
        assertThatThrownBy(() -> factura("0200", new Receptor("0", "US123", "ACME", null, "USA"), List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("ISO 3166-1");
        // En venta interna el país es opcional pero, si va, válido
        assertThat(factura("0101", new Receptor("6", "20601234565", "CLIENTE SAC", null, "pe"),
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK).receptor().pais()).isEqualTo("PE");
    }

    @Test void exportacionDeServiciosExigePaisDeUso_3098() {
        Item servicio = new Item("SRV", "Desarrollo de software", "ZZ", BigDecimal.ONE, new BigDecimal("5000.00"), TipoAfectacionIgv.EXPORTACION);
        assertThatThrownBy(() -> factura("0201", EXTERIOR, List.of(servicio)).crear(CLOCK)).hasMessageContaining("3098");
        assertThatThrownBy(() -> factura("0201", EXTERIOR, List.of(servicio)).exportacion(new Exportacion(null, "PE")).crear(CLOCK)).hasMessageContaining("3099");
        assertThatThrownBy(() -> new Exportacion(null, "XX")).hasMessageContaining("3099");
        Comprobante c = factura("0208", EXTERIOR, List.of(servicio)).exportacion(new Exportacion(null, "us")).crear(CLOCK);
        assertThat(c.exportacion().paisUso()).isEqualTo("US");
        // País de uso en una exportación de bienes
        assertThatThrownBy(() -> factura("0200", EXTERIOR, List.of(cafe("1"))).exportacion(new Exportacion("FOB", "US")).crear(CLOCK)).hasMessageContaining("4041");
        // Incoterm inválido
        assertThatThrownBy(() -> new Exportacion("FOBB", null)).hasMessageContaining("Incoterms");
        assertThat(new Exportacion(" cif ", null).incoterm()).isEqualTo("CIF");
    }

    @Test void hospedajeYPaqueteTuristicoNoSoportados() {
        assertThatThrownBy(() -> factura("0202", EXTERIOR, List.of(cafe("1"))).crear(CLOCK))
                .isInstanceOf(DomainException.class).hasMessageContaining("huésped").extracting("codigo").isEqualTo("TIPO_OPERACION_INVALIDO");
        assertThatThrownBy(() -> factura("0205", EXTERIOR, List.of(cafe("1"))).crear(CLOCK)).hasMessageContaining("huésped");
    }

    @Test void laNotaSobreUnaExportacionHeredaAfectacionYDatos() {
        Comprobante f = factura("0200", EXTERIOR, List.of(cafe("1000"))).exportacion(new Exportacion("FOB", null)).crear(CLOCK);
        f.asignarNumero(1, "20100066603");
        Comprobante nc = Comprobante.nota(f.tenantId(), TipoDocumento.NOTA_CREDITO, "FC01", EMISION, new Nota(TipoDocumento.FACTURA, "F001", 1L, "07", "Devolución parcial"), f.receptor(), List.of(cafe("100")))
                .moneda("USD").tipoOperacion("0200").exportacion(f.exportacion()).crear(CLOCK);
        assertThat(nc.totales().exportacion()).isEqualByComparingTo("450.00");
        assertThat(nc.exportacion().incoterm()).isEqualTo("FOB");
        // Una NC con ítem gravado sobre una exportación no cuadra (2642)
        assertThatThrownBy(() -> Comprobante.nota(f.tenantId(), TipoDocumento.NOTA_CREDITO, "FC01", EMISION, new Nota(TipoDocumento.FACTURA, "F001", 1L, "07", "Devolución"), f.receptor(),
                List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).moneda("USD").tipoOperacion("0200").crear(CLOCK))
                .hasMessageContaining("2642");
    }

    @Test void catalogos() {
        assertThat(TipoAfectacionIgv.porCodigo("40")).isEqualTo(TipoAfectacionIgv.EXPORTACION);
        assertThat(TipoAfectacionIgv.EXPORTACION.gravado()).isFalse();
        assertThat(Tributo.EXP.codigo()).isEqualTo("9995");
        assertThat(Tributo.EXP.nombre()).isEqualTo("EXP");
        assertThat(Tributo.EXP.tipoInternacional()).isEqualTo("FRE");
        assertThat(Tributo.EXP.categoria()).isEqualTo("G");
        assertThat(Exportacion.es("0208")).isTrue();
        assertThat(Exportacion.es("0101")).isFalse();
    }
}
