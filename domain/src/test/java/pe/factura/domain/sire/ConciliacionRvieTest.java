package pe.factura.domain.sire;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConciliacionRvieTest {
    static final LocalDate DIA = LocalDate.of(2026, 10, 14);

    static ImportesRvie i(String serie, long numero, String total) { return i(serie, numero, total, "1"); }

    static ImportesRvie i(String serie, long numero, String total, String estado) {
        BigDecimal t = new BigDecimal(total);
        return new ImportesRvie("01", serie, String.valueOf(numero), DIA, "PEN", estado, t, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, t);
    }

    @Test void dosRegistrosIgualesCuadranAunqueLaEscalaDeLosImportesCambie() {
        ConciliacionRvie c = new ConciliacionRvie();
        c.deKhipu(i("F001", 1, "100"));
        c.deKhipu(i("F001", 2, "200.5"));
        c.deSunat(i("F001", 2, "200.50"));
        c.deSunat(i("F001", 1, "100.00"));
        ConciliacionRvie.Resultado r = c.resultado();
        assertThat(r.cuadra()).isTrue();
        assertThat(r.coinciden()).isEqualTo(2);
        assertThat(r.registrosKhipu()).isEqualTo(2);
        assertThat(r.registrosSunat()).isEqualTo(2);
    }

    @Test void separaLoQueFaltaLoQueSobraYLoQueDifiere() {
        ConciliacionRvie c = new ConciliacionRvie();
        c.deKhipu(i("F001", 1, "100"));
        c.deKhipu(i("F001", 2, "200"));
        c.deKhipu(i("F001", 3, "300"));
        c.deSunat(i("F001", 1, "100"));
        c.deSunat(i("F001", 2, "201", "2"));
        c.deSunat(i("F001", 4, "400"));

        ConciliacionRvie.Resultado r = c.resultado();

        assertThat(r.cuadra()).isFalse();
        assertThat(r.soloEnSunat()).containsExactly("01-F001-4");
        assertThat(r.soloEnKhipu()).containsExactly("01-F001-3");
        assertThat(r.totalDifieren()).isEqualTo(1);
        assertThat(r.totalDiferencias()).isEqualTo(3);
        assertThat(r.difieren()).extracting(ConciliacionRvie.Diferencia::campo).containsExactly("estado", "base_gravada", "total");
        assertThat(r.difieren().get(2)).isEqualTo(new ConciliacionRvie.Diferencia("01-F001-2", "total", "200.00", "201.00"));
        assertThat(r.coinciden()).isEqualTo(1);
    }

    @Test void unEstadoQueLaPropuestaNoTraeNoEsUnaDiferencia() {
        ConciliacionRvie c = new ConciliacionRvie();
        c.deKhipu(i("F001", 1, "100"));
        c.deSunat(i("F001", 1, "100", ""));
        assertThat(c.resultado().cuadra()).isTrue();
    }

    @Test void unComprobanteRepetidoEnLaPropuestaEsUnError() {
        ConciliacionRvie c = new ConciliacionRvie();
        c.deSunat(i("F001", 1, "100"));
        assertThatThrownBy(() -> c.deSunat(i("F001", 1, "100"))).isInstanceOf(DomainException.class).hasMessageContaining("01-F001-1").extracting("codigo").isEqualTo("RVIE_ARCHIVO_INVALIDO");
    }

    @Test void elDetalleSeCortaPeroLosTotalesCuentanTodo() {
        ConciliacionRvie c = new ConciliacionRvie();
        int n = ConciliacionRvie.MAX_DETALLE + 7;
        for (int k = 1; k <= n; k++) c.deSunat(i("F001", k, "1"));
        for (int k = 1; k <= n; k++) c.deKhipu(i("F002", k, "1"));
        ConciliacionRvie.Resultado r = c.resultado();
        assertThat(r.totalSoloEnSunat()).isEqualTo(n);
        assertThat(r.soloEnSunat()).hasSize(ConciliacionRvie.MAX_DETALLE);
        assertThat(r.totalSoloEnKhipu()).isEqualTo(n);
        assertThat(r.soloEnKhipu()).hasSize(ConciliacionRvie.MAX_DETALLE);
    }

    @Test void elMismoNumeroEnOtroTipoUOtraSerieEsOtroComprobante() {
        ConciliacionRvie c = new ConciliacionRvie();
        c.deKhipu(i("F001", 1, "100"));
        c.deSunat(new ImportesRvie("07", "F001", "1", DIA, "PEN", "1", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN));
        assertThat(c.resultado().soloEnSunat()).containsExactly("07-F001-1");
        assertThat(c.resultado().soloEnKhipu()).containsExactly("01-F001-1");
    }
}
