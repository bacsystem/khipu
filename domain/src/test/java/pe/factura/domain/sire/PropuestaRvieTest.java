package pe.factura.domain.sire;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropuestaRvieTest {
    static final PropuestaRvie LECTOR = new PropuestaRvie("20100066603", PeriodoSire.de("202610"));

    /** Lo que escribe khipu se vuelve a leer igual: la conciliación de un archivo propio cuadra. */
    @Test void leeLoQueEscribeKhipu() {
        FilaRvie f = FilaRvieTest.fila();
        String linea = FormatoRvie.linea(f);
        ImportesRvie leido = LECTOR.leer(linea.substring(0, linea.length() - 2), 2);
        assertThat(leido).usingRecursiveComparison().withComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class).isEqualTo(f.importes());
    }

    @Test void aceptaElSeparadorFinalAusenteElCrYUnaFilaDeTitulos() {
        String sinFinal = FormatoRvie.linea(FilaRvieTest.fila()).replace("|\r\n", "");
        assertThat(LECTOR.leer(sinFinal, 5).clave()).isEqualTo("01-F001-125");
        assertThat(LECTOR.leer(sinFinal + "\r", 5).clave()).isEqualTo("01-F001-125");
        assertThat(LECTOR.leer("Ruc|Apellidos y Nombres o Razón social|Periodo|CAR SUNAT|Fecha de emisión|...", 1)).isNull();
        assertThat(LECTOR.leer("﻿Ruc|Razón social|Periodo", 1)).isNull();
        assertThat(LECTOR.leer("   ", 7)).isNull();
    }

    @Test void unaFilaDeTitulosQueNoEstaEnLaPrimeraLineaEsUnError() {
        assertThatThrownBy(() -> LECTOR.leer("Ruc|Razón social|Periodo|" + "x|".repeat(30), 3)).isInstanceOf(DomainException.class)
                .extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void sinElCampoTreintaYCincoElEstadoQuedaVacio() {
        String[] f = FormatoRvie.linea(FilaRvieTest.fila()).split("\\|", -1);
        String corta = String.join("|", java.util.Arrays.copyOf(f, 27));
        assertThat(LECTOR.leer(corta, 2).estado()).isEmpty();
    }

    @Test void cadaErrorDiceLaLineaYElCampo() {
        String buena = FormatoRvie.linea(FilaRvieTest.fila()).replace("\r\n", "");
        assertError(buena.replace("|118.00|PEN|", "|118,00|PEN|"), "línea 4", "campo 26");
        assertError(buena.replace("14/10/2026", "2026-10-14"), "línea 4", "campo 5");
        assertError(buena.replace("|125||", "|12.5||"), "línea 4", "campo 9");
        assertError(buena.replace("|01|F001|", "|1|F001|"), "línea 4", "campo 7");
        assertError(buena.replace("|01|F001|", "|01|" + "F".repeat(21) + "|"), "línea 4", "campo 8");
        assertError("20100066603|A|202610|x", "línea 4", "4 campos");
    }

    @Test void unaFilaDeOtroRucODeOtroPeriodoNoSeConcilia() {
        String buena = FormatoRvie.linea(FilaRvieTest.fila()).replace("\r\n", "");
        assertError(buena.replaceFirst("20100066603", "20999999995"), "RUC 20999999995");
        assertError(buena.replace("|202610|", "|202609|"), "202609");
    }

    /** H10: el anexo admite serie y número alfanuméricos de hasta 20 (campos 8 y 9); un número de dígitos se compara sin ceros a la izquierda. */
    @Test void serieYNumeroAdmitenHastaVeinteYElNumeroSeComparaSinCeros() {
        String buena = FormatoRvie.linea(FilaRvieTest.fila()).replace("\r\n", "");
        assertThat(LECTOR.leer(buena.replace("|125||", "|00000125||"), 3).clave()).isEqualTo("01-F001-125");
        assertThat(LECTOR.leer(buena.replace("|01|F001|125||", "|12|" + "A".repeat(20) + "|AB-" + "9".repeat(17) + "||"), 3).clave()).isEqualTo("12-" + "A".repeat(20) + "-AB-" + "9".repeat(17));
    }

    /** H10: una fila con el campo 10 es un rango consolidado (boletas o tickets del día): no se lee como la boleta del número inicial, se cuenta aparte. */
    @Test void unaFilaConsolidadaNoSeLeeComoUnaBoletaYSeCuenta() {
        PropuestaRvie lector = new PropuestaRvie("20100066603", PeriodoSire.de("202610"));
        String buena = FormatoRvie.linea(FilaRvieTest.fila()).replace("\r\n", "");
        assertThat(lector.leer(buena.replace("|01|F001|125||", "|03|B001|1|40|"), 3)).isNull();
        assertThat(lector.consolidadas()).isEqualTo(1);
        assertThat(lector.leer(buena, 4)).isNotNull();
        assertThat(lector.consolidadas()).isEqualTo(1);
    }

    @Test void laFechaSeLeeDiaMesAnio() {
        assertThat(LECTOR.leer(FormatoRvie.linea(FilaRvieTest.fila()), 2).fechaEmision()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    private static void assertError(String linea, String... contiene) {
        assertThatThrownBy(() -> LECTOR.leer(linea, 4)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
        assertThatThrownBy(() -> LECTOR.leer(linea, 4)).hasMessageContainingAll(contiene);
    }
}
