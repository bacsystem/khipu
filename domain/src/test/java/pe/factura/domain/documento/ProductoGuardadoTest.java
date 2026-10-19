package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ProductoGuardado.Datos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Productos guardados (#320): se validan como la línea de un comprobante, así que lo guardado siempre se puede emitir. */
class ProductoGuardadoTest {
    static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    static final UUID T = UUID.randomUUID();

    static Datos datos(String codigo, String descripcion, String unidad, String afectacion, String precio, String codigoSunat) {
        return new Datos(codigo, descripcion, unidad, afectacion, precio == null ? null : new BigDecimal(precio), codigoSunat);
    }

    static Datos valido() { return datos("SKU-1", "Servicio de consultoría", "ZZ", "10", "118", "80101500"); }

    @Test void guardaTodosLosCamposNormalizados() {
        ProductoGuardado p = ProductoGuardado.nuevo(T, datos("  SKU-1 ", "  Servicio de consultoría ", " zz ", "10", "118", " 80101500 "), AHORA);

        assertThat(p.codigo()).isEqualTo("SKU-1");
        assertThat(p.descripcion()).isEqualTo("Servicio de consultoría");
        assertThat(p.unidad()).as("la unidad del catálogo 03 va en mayúsculas").isEqualTo("ZZ");
        assertThat(p.precioUnitario()).isEqualByComparingTo("118");
        assertThat(p.codigoSunat()).isEqualTo("80101500");
        assertThat(p.creadoEn()).isEqualTo(AHORA).isEqualTo(p.actualizadoEn());
    }

    @Test void loOpcionalEnBlancoQuedaNulo() {
        ProductoGuardado p = ProductoGuardado.nuevo(T, datos("  ", "Bolsa", "NIU", "10", "1", ""), AHORA);

        assertThat(p.codigo()).isNull();
        assertThat(p.codigoSunat()).isNull();
    }

    @Test void elPrecioTieneAlMenosDosDecimalesYSinCerosDeRelleno() {
        assertThat(datos(null, "Bolsa", "NIU", "10", "100", null).precioUnitario().toPlainString()).isEqualTo("100.00");
        assertThat(datos(null, "Bolsa", "NIU", "10", "1E+2", null).precioUnitario().toPlainString()).isEqualTo("100.00");
        assertThat(datos(null, "Bolsa", "NIU", "10", "0.5000000000", null).precioUnitario().toPlainString()).isEqualTo("0.50");
        assertThat(datos(null, "Bolsa", "NIU", "10", "1.2345", null).precioUnitario().toPlainString()).isEqualTo("1.2345");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "afectación fuera del catálogo 07 | SKU-1 | Servicio | ZZ | 99 | 118 | 80101500 | afectación del IGV",
            "afectación vacía | SKU-1 | Servicio | ZZ |  | 118 | 80101500 | afectación del IGV",
            "precio en cero | SKU-1 | Servicio | ZZ | 10 | 0 | 80101500 | 2369",
            "precio negativo | SKU-1 | Servicio | ZZ | 10 | -5 | 80101500 | 2369",
            "precio con más de 10 decimales | SKU-1 | Servicio | ZZ | 10 | 1.12345678901 | 80101500 | hasta 12 enteros y 10 decimales",
            "unidad fuera del catálogo 03 | SKU-1 | Servicio | XYZ | 10 | 118 | 80101500 | 2936",
            "sin unidad | SKU-1 | Servicio |  | 10 | 118 | 80101500 | 2883",
            "sin descripción | SKU-1 |  | ZZ | 10 | 118 | 80101500 | 2026",
            "código interno de más de 30 caracteres | 1234567890123456789012345678901 | Servicio | ZZ | 10 | 118 | 80101500 | hasta 30 caracteres",
            "código SUNAT corto | SKU-1 | Servicio | ZZ | 10 | 118 | 123 | 3496",
            "código SUNAT 00000000 | SKU-1 | Servicio | ZZ | 10 | 118 | 00000000 | 3496"})
    void rechazaLoQueUnaLineaDeComprobanteRechaza(String caso, String codigo, String descripcion, String unidad, String afectacion, String precio, String codigoSunat, String mensaje) {
        assertThatThrownBy(() -> ProductoGuardado.nuevo(T, datos(codigo, descripcion, unidad, afectacion, precio, codigoSunat), AHORA))
                .as(caso).isInstanceOf(DomainException.class).hasMessageContaining(mensaje);
    }

    @Test void sinPrecioEsUnErrorDeLaLineaYNoUnNullPointer() {
        assertThatThrownBy(() -> ProductoGuardado.nuevo(T, datos(null, "Servicio", "ZZ", "10", null, null), AHORA)).isInstanceOf(DomainException.class).hasMessageContaining("2369");
    }

    @Test void unaDescripcionDeMasDe500CaracteresSeRechazaYUnaDe500Pasa() {
        assertThat(ProductoGuardado.nuevo(T, datos(null, "x".repeat(500), "ZZ", "10", "1", null), AHORA).descripcion()).hasSize(500);
        assertThatThrownBy(() -> ProductoGuardado.nuevo(T, datos(null, "x".repeat(501), "ZZ", "10", "1", null), AHORA)).hasMessageContaining("2027");
    }

    @Test void unaGratuitaYUnaExportacionSeGuardanConSuAfectacion() {
        assertThat(ProductoGuardado.nuevo(T, datos(null, "Muestra", "NIU", "21", "5", null), AHORA).tipoAfectacionIgv()).isEqualTo("21");
        assertThat(ProductoGuardado.nuevo(T, datos(null, "Exportación de servicios", "ZZ", "40", "500", null), AHORA).tipoAfectacionIgv()).isEqualTo("40");
    }

    @Test void conCambiaLosDatosYConservaElIdYLaFechaDeAlta() {
        ProductoGuardado p = ProductoGuardado.nuevo(T, valido(), AHORA);

        ProductoGuardado c = p.con(datos("SKU-2", "Servicio de auditoría", "HUR", "10", "236", null), AHORA.plusSeconds(60));

        assertThat(c.id()).isEqualTo(p.id());
        assertThat(c.creadoEn()).isEqualTo(AHORA);
        assertThat(c.actualizadoEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(c.codigo()).isEqualTo("SKU-2");
        assertThat(c.codigoSunat()).isNull();
        assertThatThrownBy(() -> p.con(datos("SKU-2", "Servicio", "ZZ", "10", "0", null), AHORA)).isInstanceOf(DomainException.class);
    }
}
