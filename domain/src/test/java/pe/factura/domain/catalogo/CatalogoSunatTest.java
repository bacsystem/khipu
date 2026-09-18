package pe.factura.domain.catalogo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoSunatTest {
    @Test void cargaLosCatalogosDelAnexo8() {
        assertThat(CatalogoSunat.todos()).extracting(CatalogoSunat.Catalogo::id)
                .contains("01", "02", "03", "05", "06", "07", "09", "10", "16", "51", "52", "53", "54", "59");
        assertThat(CatalogoSunat.porId("07").orElseThrow().entradas()).hasSize(19);
        // 25: unión de los listados 25.1–25.3 con la columna "Listado"; un código en dos listados aparece una vez.
        assertThat(CatalogoSunat.porId("25").orElseThrow().entradas()).hasSize(89);
        assertThat(CatalogoSunat.porId("25").orElseThrow().entrada("50161509").orElseThrow().extra().get("Listado")).contains("25.1").contains("25.2");
    }

    @Test void descripcionYColumnasAdicionales() {
        assertThat(CatalogoSunat.descripcion("07", "10")).contains("Gravado - Operación Onerosa");
        assertThat(CatalogoSunat.descripcion("06", "6")).hasValueSatisfying(d -> assertThat(d).containsIgnoringCase("registro unico"));
        assertThat(CatalogoSunat.porId("07").orElseThrow().entrada("15").orElseThrow().extra()).containsEntry("Codigo de tributo", "9996");
        assertThat(CatalogoSunat.porId("53").orElseThrow().entrada("62").orElseThrow().descripcion()).containsIgnoringCase("retenci");
    }

    @Test void codigoInexistente() {
        assertThat(CatalogoSunat.descripcion("07", "99")).isEmpty();
        assertThat(CatalogoSunat.porId("99")).isEmpty();
        assertThat(CatalogoSunat.porId("51").orElseThrow().contiene("0101")).isTrue();
        assertThat(CatalogoSunat.porId("51").orElseThrow().contiene("9999")).isFalse();
    }
}
