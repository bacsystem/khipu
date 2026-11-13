package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LimitesTest {
    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    @Test void unConjuntoValidoGuardaSusLimites() {
        Limites l = new Limites(Limite.de(300), 3, Limite.sinLimite(), Limite.de(2), 5);

        assertThat(l.documentosAlMes()).isEqualTo(Limite.de(300));
        assertThat(l.rucs()).isEqualTo(3);
        assertThat(l.usuarios().ilimitado()).isTrue();
        assertThat(l.apiKeys()).isEqualTo(Limite.de(2));
        assertThat(l.retencionAnios()).isEqualTo(5);
    }

    @Test void losRucYLaRetencionSonPositivos() {
        assertThat(codigo(() -> new Limites(Limite.de(1), 0, Limite.de(1), Limite.de(1), 1))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Limites(Limite.de(1), 1, Limite.de(1), Limite.de(1), 0))).isEqualTo("RETENCION_INVALIDA");
        assertThat(new Limites(Limite.de(1), 1, Limite.de(1), Limite.de(1), 1).rucs()).isEqualTo(1);
    }

    @Test void ningunLimiteFalta() {
        assertThat(codigo(() -> new Limites(null, 1, Limite.de(1), Limite.de(1), 1))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Limites(Limite.de(1), 1, null, Limite.de(1), 1))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Limites(Limite.de(1), 1, Limite.de(1), null, 1))).isEqualTo("LIMITE_INVALIDO");
    }

    @Test void dosConjuntosConLosMismosValoresSonIguales() {
        Limites a = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);

        assertThat(a).isEqualTo(new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5));
        assertThat(a).isNotEqualTo(new Limites(Limite.de(301), 1, Limite.de(1), Limite.de(2), 5));
        assertThat(a).isNotEqualTo(new Limites(Limite.sinLimite(), 1, Limite.de(1), Limite.de(2), 5));
    }

    /** #613: sin decir otra cosa, el tope es mensual y Beta da 1 000 pruebas por RUC. */
    @Test void porDefectoElTopeEsMensualYBetaDaMilPorRuc() {
        Limites l = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);
        assertThat(l.periodoDeDocumentos()).isEqualTo(PeriodoDelTope.MENSUAL);
        assertThat(l.pruebasBetaPorRuc()).isEqualTo(Limite.de(1_000));
    }

    /** #613: la bolsa de Beta es la de cada RUC por los RUC del plan: Negocio (3 RUC) tiene 3 000, Pro (10) tiene 10 000. */
    @Test void lasPruebasDeBetaSonLasDeCadaRucPorLosRucDelPlan() {
        assertThat(conRucs(1, Limite.de(1_000)).pruebasBetaAlMes()).isEqualTo(Limite.de(1_000));
        assertThat(conRucs(3, Limite.de(1_000)).pruebasBetaAlMes()).isEqualTo(Limite.de(3_000));
        assertThat(conRucs(10, Limite.de(1_000)).pruebasBetaAlMes()).isEqualTo(Limite.de(10_000));
        assertThat(conRucs(3, Limite.sinLimite()).pruebasBetaAlMes().ilimitado()).isTrue();
    }

    @Test void elProductoDeBetaNoDesborda() {
        assertThat(conRucs(10, Limite.de(Integer.MAX_VALUE)).pruebasBetaAlMes()).isEqualTo(Limite.de(Integer.MAX_VALUE));
    }

    /** Un tope «en total» sin cifra no significa nada: ilimitado solo puede ser mensual. */
    @Test void unTopeTotalNecesitaCifra() {
        assertThat(codigo(() -> new Limites(Limite.sinLimite(), 1, Limite.de(1), Limite.de(1), 1, PeriodoDelTope.TOTAL, Limite.de(1_000)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(1), 1, PeriodoDelTope.TOTAL, Limite.de(1_000)).periodoDeDocumentos()).isEqualTo(PeriodoDelTope.TOTAL);
    }

    @Test void niElPeriodoNiLasPruebasFaltan() {
        assertThat(codigo(() -> new Limites(Limite.de(1), 1, Limite.de(1), Limite.de(1), 1, null, Limite.de(1)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> new Limites(Limite.de(1), 1, Limite.de(1), Limite.de(1), 1, PeriodoDelTope.MENSUAL, null))).isEqualTo("LIMITE_INVALIDO");
    }

    @Test void elPeriodoYLasPruebasDistinguenDosConjuntos() {
        Limites mensual = new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(1), 1);
        assertThat(mensual).isNotEqualTo(new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(1), 1, PeriodoDelTope.TOTAL, Limite.de(1_000)));
        assertThat(mensual).isNotEqualTo(new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(1), 1, PeriodoDelTope.MENSUAL, Limite.de(500)));
    }

    private static Limites conRucs(int rucs, Limite pruebasPorRuc) {
        return new Limites(Limite.de(300), rucs, Limite.de(1), Limite.de(1), 5, PeriodoDelTope.MENSUAL, pruebasPorRuc);
    }

    /** #498: cada recurso del plan lee su propio tope; los RUC son siempre finitos. */
    @Test void cadaRecursoLeeSuTope() {
        Limites l = new Limites(Limite.de(300), 3, Limite.de(5), Limite.sinLimite(), 2);
        assertThat(l.de(RecursoDelPlan.RUCS)).isEqualTo(Limite.de(3));
        assertThat(l.de(RecursoDelPlan.USUARIOS)).isEqualTo(Limite.de(5));
        assertThat(l.de(RecursoDelPlan.API_KEYS).ilimitado()).isTrue();
    }
}
