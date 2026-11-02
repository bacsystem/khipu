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

    /**
     * #308: el 03 es la rec 20 completa (lo que SUNAT llama catálogo 03, hoja «Catálogos» del Anexo 8), incluidos los códigos que la UN/ECE
     * dio de baja y SUNAT sigue aceptando (BX, PK, CS: el propio ejemplo de la guía XML usa CS). Las más usadas van primero y marcadas.
     */
    @Test void elCatalogo03EsLaRec20CompletaConLasMasUsadasPrimero() {
        CatalogoSunat.Catalogo unidades = CatalogoSunat.porId("03").orElseThrow();
        assertThat(unidades.entradas().size()).isGreaterThan(2000);
        assertThat(unidades.entradas()).extracting(CatalogoSunat.Entrada::codigo).doesNotHaveDuplicates()
                .contains("NIU", "ZZ", "BX", "PK", "CS", "RO", "4A", "E48", "MNJ").doesNotContain("ROL");
        assertThat(unidades.entradas().get(0).codigo()).isEqualTo("NIU");
        assertThat(unidades.entrada("BX").orElseThrow().descripcion()).isEqualTo("Caja");
        assertThat(unidades.entrada("BX").orElseThrow().extra()).containsEntry("Más usada", "Sí");
        assertThat(unidades.entrada("E48").orElseThrow().extra()).doesNotContainKey("Más usada");
        assertThat(unidades.entradas().stream().filter(e -> e.extra().containsKey("Más usada")).count()).isEqualTo(48);
    }

    @Test void descripcionYColumnasAdicionales() {
        assertThat(CatalogoSunat.descripcion("07", "10")).contains("Gravado - Operación Onerosa");
        assertThat(CatalogoSunat.descripcion("06", "6")).hasValueSatisfying(d -> assertThat(d).containsIgnoringCase("registro unico"));
        assertThat(CatalogoSunat.porId("07").orElseThrow().entrada("15").orElseThrow().extra()).containsEntry("Codigo de tributo", "9996");
        assertThat(CatalogoSunat.porId("53").orElseThrow().entrada("62").orElseThrow().descripcion()).containsIgnoringCase("retenci");
    }

    /** #39: los catálogos de la guía de remisión, tal como los trae la hoja «Catálogos» (modalidad 18, motivo 20, documentos relacionados 61 con su GRE aplicable). */
    @Test void losCatalogosDeLaGuiaDeRemision() {
        assertThat(CatalogoSunat.porId("18").orElseThrow().entradas()).extracting(CatalogoSunat.Entrada::codigo).containsExactly("01", "02");
        assertThat(CatalogoSunat.porId("20").orElseThrow().entradas()).hasSize(13);
        assertThat(CatalogoSunat.descripcion("20", "04")).contains("Traslado entre establecimientos de la misma empresa");
        assertThat(CatalogoSunat.porId("61").orElseThrow().entradas()).hasSize(26);
        assertThat(CatalogoSunat.porId("61").orElseThrow().entrada("31").orElseThrow().extra()).containsEntry("GRE aplicable", "solo transportista");
        assertThat(CatalogoSunat.porId("61").orElseThrow().entrada("49").orElseThrow().extra()).containsEntry("GRE aplicable", "solo remitente");
    }

    @Test void codigoInexistente() {
        assertThat(CatalogoSunat.descripcion("07", "99")).isEmpty();
        assertThat(CatalogoSunat.porId("99")).isEmpty();
        assertThat(CatalogoSunat.porId("51").orElseThrow().contiene("0101")).isTrue();
        assertThat(CatalogoSunat.porId("51").orElseThrow().contiene("9999")).isFalse();
    }
}
