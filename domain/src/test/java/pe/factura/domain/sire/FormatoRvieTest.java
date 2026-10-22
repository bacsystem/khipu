package pe.factura.domain.sire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatoRvieTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0.00", "0.000, 0.00", "-0.00, 0.00", "-0.004, 0.00", "1, 1.00", "1.005, 1.01", "1.004, 1.00", "-1.005, -1.01", "1234567.8, 1234567.80",
            "1E+3, 1000.00", "-25.5, -25.50", "99999999999.99, 99999999999.99"})
    void unImporteVaConDosDecimalesPuntoYSinNotacionCientifica(String valor, String esperado) {
        assertThat(FormatoRvie.importe(new BigDecimal(valor))).isEqualTo(esperado);
    }

    @Test void unImporteNuloEsCero() { assertThat(FormatoRvie.importe(null)).isEqualTo("0.00"); }

    @Test void elTipoDeCambioVaConTresDecimales() {
        assertThat(FormatoRvie.tipoCambio(new BigDecimal("3.7"))).isEqualTo("3.700");
        assertThat(FormatoRvie.tipoCambio(BigDecimal.ONE)).isEqualTo("1.000");
        assertThat(FormatoRvie.tipoCambio(new BigDecimal("999.999"))).isEqualTo("999.999");
        assertThat(FormatoRvie.tipoCambio(null)).isEmpty();
    }

    @Test void lasFechasVanDiaMesAnio() {
        assertThat(FormatoRvie.fecha(LocalDate.of(2026, 1, 5))).isEqualTo("05/01/2026");
        assertThat(FormatoRvie.fecha(null)).isEmpty();
    }

    @Test void elTextoPierdeSeparadoresYControlesYJuntaEspacios() {
        assertThat(FormatoRvie.texto("a|b", 10)).isEqualTo("a b");
        assertThat(FormatoRvie.texto("a\r\n\tb\u0000c", 10)).isEqualTo("a b c");
        assertThat(FormatoRvie.texto("  x   y﻿", 10)).isEqualTo("x y");
        assertThat(FormatoRvie.texto("|||", 10)).isEmpty();
        assertThat(FormatoRvie.texto(null, 10)).isEmpty();
        assertThat(FormatoRvie.texto("abcdef", 3)).isEqualTo("abc");
        assertThat(FormatoRvie.texto("ab cdef", 3)).as("no deja un espacio al final del corte").isEqualTo("ab");
    }

    @Test void elNombreDelArchivoEsLeRucPeriodoYElLibroDelRvie() {
        assertThat(FormatoRvie.nombreArchivo("20100066603", PeriodoSire.de("202610"))).isEqualTo("LE2010006660320261000140400021112");
    }

    @Test void unaFilaQueNoTieneCuarentaCamposONoSeEscribioBienNoSale() {
        FilaRvie f = FilaRvieTest.fila();
        assertThat(FormatoRvie.linea(f)).endsWith("|\r\n");
        FilaRvie conSeparador = new FilaRvie("20100066603", "A", "202610", "x|y", f.fechaEmision(), null, "01", "F001", 1, "6", "1", "N", f.valorExportacion(), f.baseGravada(),
                f.descuentoBase(), f.igv(), f.descuentoIgv(), f.exonerado(), f.inafecto(), f.isc(), f.baseIvap(), f.ivap(), f.icbper(), f.otrosTributos(), f.total(), "PEN", BigDecimal.ONE,
                null, null, null, null, null, "1", f.valorGratuito(), "0101");
        assertThatThrownBy(() -> FormatoRvie.linea(conSeparador)).isInstanceOf(IllegalStateException.class);
    }

    @Test void elPeriodoEsAaaammYEmpiezaEnElPrimerDia() {
        PeriodoSire p = PeriodoSire.de("202602");
        assertThat(p.desde()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(p.hasta()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(p.aaaamm()).isEqualTo("202602");
        assertThat(PeriodoSire.de(" 202412 ").aaaamm()).isEqualTo("202412");
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-10", "20261", "202613", "202600", "2026101", "abcdef", "''", "201312"}, nullValues = "NULO")
    void unPeriodoMalEscritoEsParametroInvalido(String texto) {
        assertThatThrownBy(() -> PeriodoSire.de(texto)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
    }

    @Test void unPeriodoNuloOFuturoEsParametroInvalido() {
        assertThatThrownBy(() -> PeriodoSire.de(null)).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        assertThatThrownBy(() -> PeriodoSire.de("202611").exigirIniciado(LocalDate.of(2026, 10, 31))).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        PeriodoSire.de("202610").exigirIniciado(LocalDate.of(2026, 10, 1));
    }
}
