package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Orden de compra, guías de remisión y otros documentos relacionados (campos 59, 22 y 23 de la hoja Factura2_0). */
class ReferenciasTest {

    @ParameterizedTest
    @ValueSource(strings = {"T001-123", "TA1B-1", "0001-12345678", "EG01-45", "EG07-9", "G001-1", "V001-77", "VA0Z-12"})
    void numerosDeGuiaValidos(String numero) {
        assertThat(new GuiaRelacionada("09", numero).numero()).isEqualTo(numero);
        assertThat(new GuiaRelacionada("31", numero).tipo()).isEqualTo("31");
    }

    @ParameterizedTest
    @ValueSource(strings = {"F001-123", "T001-123456789", "T0011-1", "t001-1", "EG05-1", "001-1", "T001_1", ""})
    void numerosDeGuiaInvalidos(String numero) {
        assertThatThrownBy(() -> new GuiaRelacionada("09", numero)).isInstanceOf(DomainException.class).hasMessageContaining("4006");
    }

    @Test void tipoDeGuiaSolo09o31() {
        assertThatThrownBy(() -> new GuiaRelacionada("01", "T001-1")).hasMessageContaining("4005");
        assertThatThrownBy(() -> new GuiaRelacionada(null, "T001-1")).hasMessageContaining("4005");
    }

    @Test void otrosDocumentos() {
        assertThat(new DocumentoRelacionado("05", "SCOP-8841203").tipo()).isEqualTo("05");
        assertThat(new DocumentoRelacionado("99", "A".repeat(30)).numero()).hasSize(30);
        assertThatThrownBy(() -> new DocumentoRelacionado("02", "F001-1")).hasMessageContaining("4009");       // anticipos van aparte
        assertThatThrownBy(() -> new DocumentoRelacionado("01", "F001-1")).hasMessageContaining("4009");
        assertThatThrownBy(() -> new DocumentoRelacionado("10", "1")).hasMessageContaining("4009");
        assertThatThrownBy(() -> new DocumentoRelacionado("05", "A".repeat(31))).hasMessageContaining("4010");
        assertThatThrownBy(() -> new DocumentoRelacionado("05", "con espacio")).hasMessageContaining("4010");
        assertThatThrownBy(() -> new DocumentoRelacionado("05", "con\ttab")).hasMessageContaining("4010");
        assertThatThrownBy(() -> new DocumentoRelacionado("05", "")).hasMessageContaining("4010");
    }

    @Test void ordenDeCompraYRepetidos() {
        Referencias r = new Referencias("OC 2026/0457", List.of(new GuiaRelacionada("09", "T001-1")), List.of());
        assertThat(r.ordenCompra()).isEqualTo("OC 2026/0457");                      // el espacio sí se admite (4233)
        assertThat(r.vacias()).isFalse();
        assertThat(Referencias.ninguna().vacias()).isTrue();
        assertThat(new Referencias(null, null, null).guias()).isEmpty();
        assertThatThrownBy(() -> new Referencias("", List.of(), List.of())).hasMessageContaining("4233");
        assertThatThrownBy(() -> new Referencias("A".repeat(21), List.of(), List.of())).hasMessageContaining("4233");
        assertThatThrownBy(() -> new Referencias("OC\n1", List.of(), List.of())).hasMessageContaining("4233");
        assertThatThrownBy(() -> new Referencias(null, List.of(new GuiaRelacionada("09", "T001-1"), new GuiaRelacionada("09", "T001-1")), List.of())).hasMessageContaining("2364");
        // Misma numeración con distinto tipo no es repetición.
        assertThat(new Referencias(null, List.of(new GuiaRelacionada("09", "T001-1"), new GuiaRelacionada("31", "T001-1")), List.of()).guias()).hasSize(2);
        assertThatThrownBy(() -> new Referencias(null, List.of(), List.of(new DocumentoRelacionado("99", "X"), new DocumentoRelacionado("99", "X")))).hasMessageContaining("2365");
    }
}
