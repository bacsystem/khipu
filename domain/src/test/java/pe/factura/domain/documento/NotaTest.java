package pe.factura.domain.documento;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notas de crédito/débito sobre facturas: documento modificado, motivo (catálogos 09/10) y serie (hojas NotaCredito2_0 / NotaDebito2_0). */
class NotaTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE SAC", null);
    static final List<Item> ITEMS = List.of(new Item("P", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO));
    static final Nota NC_ANULACION = new Nota(TipoDocumento.FACTURA, "F001", 12, "01", "Anulación de la operación");

    static Comprobante nota(TipoDocumento tipo, String serie, Nota nota, FormaPago fp) {
        return Comprobante.nota(UUID.randomUUID(), tipo, serie, LocalDate.of(2026, 9, 18), nota, RECEPTOR, ITEMS).formaPago(fp).crear(CLOCK);
    }

    @Test void notaDeCreditoSobreFactura() {
        Comprobante c = nota(TipoDocumento.NOTA_CREDITO, "FC01", NC_ANULACION, null);
        assertThat(c.esNota()).isTrue();
        assertThat(c.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(c.nota().documentoAfectado()).isEqualTo("F001-12");
        assertThat(c.nota().descripcionMotivo(TipoDocumento.NOTA_CREDITO)).isEqualTo("Anulación de la operación");
        assertThat(c.formaPago().esCredito()).isFalse();
        assertThat(c.totales().total()).isEqualByComparingTo("118.00");
        assertThat(c.fechaVencimiento()).isNull();
        assertThat(c.anticipos()).isEmpty();
        assertThat(c.referencias().vacias()).isTrue();
    }

    @Test void notaDeDebitoConMotivoDelCatalogo10() {
        Nota interes = new Nota(TipoDocumento.FACTURA, "F001", 12, "01", "Intereses por mora");
        Comprobante c = nota(TipoDocumento.NOTA_DEBITO, "FD01", interes, null);
        assertThat(c.nota().descripcionMotivo(TipoDocumento.NOTA_DEBITO)).isEqualTo("Intereses por mora");
        // 13 existe en ambos catálogos con sentidos distintos: en la ND es "Penalidades", no corrige cuotas.
        Comprobante penalidad = nota(TipoDocumento.NOTA_DEBITO, "FD01", new Nota(TipoDocumento.FACTURA, "F001", 12, "13", "Penalidad"), null);
        assertThat(penalidad.nota().descripcionMotivo(TipoDocumento.NOTA_DEBITO)).isEqualTo("Penalidades");
        assertThat(penalidad.nota().corrigeCuotas(TipoDocumento.NOTA_DEBITO)).isFalse();
        assertThat(penalidad.items()).isEqualTo(ITEMS);
        assertThat(penalidad.totales().total()).isEqualByComparingTo("118.00");
    }

    @Test void laNotaDeCredito13NoMueveImportes() {
        FormaPago fp = FormaPago.credito(new BigDecimal("118.00"), List.of(new FormaPago.Cuota(new BigDecimal("118.00"), LocalDate.of(2026, 10, 18))));
        Comprobante c = nota(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 12, "13", "Reprogramación de cuotas"), fp);
        assertThat(c.items()).hasSize(1);
        assertThat(c.items().get(0).descripcion()).isEqualTo("Reprogramación de cuotas");
        assertThat(c.items().get(0).precioUnitario()).isEqualByComparingTo("0");
        assertThat(c.totales().total()).isEqualByComparingTo("0.00");                  // 3315
        assertThat(c.totales().subtotales()).isEmpty();
        assertThat(c.formaPago().cuotas()).hasSize(1);
    }

    @Test void laFacturaNoEsNota() {
        Comprobante f = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 18), "PEN", "0101", RECEPTOR, ITEMS).crear(CLOCK);
        assertThat(f.esNota()).isFalse();
        assertThat(f.nota()).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("invalidas")
    void validaciones(String caso, String esperado, ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOf(DomainException.class).hasMessageContaining(esperado);
    }

    static Stream<Arguments> invalidas() {
        FormaPago credito = FormaPago.credito(new BigDecimal("118.00"), List.of(new FormaPago.Cuota(new BigDecimal("118.00"), LocalDate.of(2026, 10, 18))));
        return Stream.of(
                Arguments.of("nota sobre boleta", "2116", (ThrowingCallable) () -> new Nota(TipoDocumento.BOLETA, "B001", 1, "01", "x")),
                Arguments.of("serie afectada que no es de factura", "2117", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "B001", 1, "01", "x")),
                Arguments.of("número afectado cero", "2117", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "F001", 0, "01", "x")),
                Arguments.of("sin motivo", "2128", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "F001", 1, " ", "x")),
                Arguments.of("descripción vacía", "2135", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "F001", 1, "01", "")),
                Arguments.of("descripción con salto de línea", "2135", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "F001", 1, "01", "a\nb")),
                Arguments.of("descripción de 501 caracteres", "2135", (ThrowingCallable) () -> new Nota(TipoDocumento.FACTURA, "F001", 1, "01", "a".repeat(501))),
                Arguments.of("motivo 99 fuera del catálogo 09", "2172", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 1, "99", "x"), null)),
                Arguments.of("motivo 04 (NC) usado en una ND", "2172", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_DEBITO, "FD01", new Nota(TipoDocumento.FACTURA, "F001", 1, "04", "x"), null)),
                Arguments.of("tipo factura en crearNota", "07 (crédito) u 08", (ThrowingCallable) () -> nota(TipoDocumento.FACTURA, "F001", NC_ANULACION, null)),
                Arguments.of("serie B para una nota sobre factura", "1001", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "BC01", NC_ANULACION, null)),
                Arguments.of("serie que no es de nota", "1001", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "X001", NC_ANULACION, null)),
                Arguments.of("ND 13 (penalidad) sin ítems: no es la NC 13, exige ítems", "al menos un ítem", (ThrowingCallable) () -> Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 18), new Nota(TipoDocumento.FACTURA, "F001", 1, "13", "Penalidad"), RECEPTOR, null).crear(CLOCK)),
                Arguments.of("NC 13 sin forma de pago al crédito", "3257", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 1, "13", "x"), null)),
                Arguments.of("NC 13 al contado", "3257", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "FC01", new Nota(TipoDocumento.FACTURA, "F001", 1, "13", "x"), FormaPago.contado())),
                Arguments.of("sin nota", "2524", (ThrowingCallable) () -> nota(TipoDocumento.NOTA_CREDITO, "FC01", null, null)),
                Arguments.of("cuotas de la NC 13 con vencimiento anterior a la factura", "3321", (ThrowingCallable) () -> credito.validarComoCorreccionDe(new BigDecimal("500.00"), LocalDate.of(2026, 10, 18))),
                Arguments.of("pendiente de la NC 13 mayor que la factura", "3320", (ThrowingCallable) () -> credito.validarComoCorreccionDe(new BigDecimal("100.00"), LocalDate.of(2026, 9, 1))));
    }
}
