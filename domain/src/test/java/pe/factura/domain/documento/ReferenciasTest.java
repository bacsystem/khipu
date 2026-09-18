package pe.factura.domain.documento;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.factura.domain.DomainException;

import java.util.List;
import java.util.stream.Stream;

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

    // EEEE-1 y 0707-1 serían válidos con una lectura literal de "[EG07] {4}"; la regla se lee como la serie EG07.
    @ParameterizedTest
    @ValueSource(strings = {"F001-123", "T001-123456789", "T0011-1", "t001-1", "EG05-1", "EEEE-1", "GG07-1", "001-1", "T001_1", ""})
    void numerosDeGuiaInvalidos(String numero) {
        assertThatThrownBy(() -> new GuiaRelacionada("09", numero)).isInstanceOf(DomainException.class).hasMessageContaining("4006");
    }

    @Test void documentosValidos() {
        assertThat(new DocumentoRelacionado("05", "SCOP-8841203").tipo()).isEqualTo("05");
        assertThat(new DocumentoRelacionado("99", "A".repeat(30)).numero()).hasSize(30);
        Referencias r = new Referencias("OC 2026/0457", List.of(new GuiaRelacionada("09", "T001-1")), List.of());
        assertThat(r.ordenCompra()).isEqualTo("OC 2026/0457");                      // el espacio sí se admite (4233)
        assertThat(r.vacias()).isFalse();
        assertThat(Referencias.ninguna().vacias()).isTrue();
        assertThat(new Referencias(null, null, null).guias()).isEmpty();
        // Misma numeración con distinto tipo no es repetición.
        assertThat(new Referencias(null, List.of(new GuiaRelacionada("09", "T001-1"), new GuiaRelacionada("31", "T001-1")), List.of()).guias()).hasSize(2);
    }

    /** Cada regla SUNAT que rechaza una referencia, con el código que debe llevar el mensaje. */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("referenciasInvalidas")
    void validaciones(String caso, String reglaEsperada, ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOf(DomainException.class).hasMessageContaining(reglaEsperada)
                .extracting("codigo").isEqualTo("DOCUMENTO_RELACIONADO_INVALIDO");
    }

    static Stream<Arguments> referenciasInvalidas() {
        GuiaRelacionada guia = new GuiaRelacionada("09", "T001-1");
        DocumentoRelacionado otro = new DocumentoRelacionado("99", "X");
        return Stream.of(
                Arguments.of("tipo de guía que no es 09/31", "4005", (ThrowingCallable) () -> new GuiaRelacionada("01", "T001-1")),
                Arguments.of("tipo de guía nulo", "4005", (ThrowingCallable) () -> new GuiaRelacionada(null, "T001-1")),
                Arguments.of("factura de anticipo como otro documento", "4009", (ThrowingCallable) () -> new DocumentoRelacionado("02", "F001-1")),
                Arguments.of("tipo 01 no admitido", "4009", (ThrowingCallable) () -> new DocumentoRelacionado("01", "F001-1")),
                Arguments.of("tipo 10 no admitido", "4009", (ThrowingCallable) () -> new DocumentoRelacionado("10", "1")),
                Arguments.of("número de 31 caracteres", "4010", (ThrowingCallable) () -> new DocumentoRelacionado("05", "A".repeat(31))),
                Arguments.of("número con espacio", "4010", (ThrowingCallable) () -> new DocumentoRelacionado("05", "con espacio")),
                Arguments.of("número con tabulador", "4010", (ThrowingCallable) () -> new DocumentoRelacionado("05", "con\ttab")),
                Arguments.of("número vacío", "4010", (ThrowingCallable) () -> new DocumentoRelacionado("05", "")),
                Arguments.of("orden de compra vacía", "4233", (ThrowingCallable) () -> new Referencias("", List.of(), List.of())),
                Arguments.of("orden de compra de 21 caracteres", "4233", (ThrowingCallable) () -> new Referencias("A".repeat(21), List.of(), List.of())),
                Arguments.of("orden de compra con salto de línea", "4233", (ThrowingCallable) () -> new Referencias("OC\n1", List.of(), List.of())),
                Arguments.of("guía repetida", "2364", (ThrowingCallable) () -> new Referencias(null, List.of(guia, guia), List.of())),
                Arguments.of("otro documento repetido", "2365", (ThrowingCallable) () -> new Referencias(null, List.of(), List.of(otro, otro))));
    }
}
