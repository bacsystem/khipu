package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuánto de su tope de documentos lleva usado una cuenta, y desde cuándo hay que avisar (#193). */
class UsoDeLimiteTest {
    @Test void elPorcentajeEsElDelTopeUsado() {
        assertThat(UsoDeLimite.porcentaje(150, Limite.de(300))).hasValue(50);
        assertThat(UsoDeLimite.porcentaje(300, Limite.de(300))).hasValue(100);
        assertThat(UsoDeLimite.porcentaje(0, Limite.de(300))).hasValue(0);
    }

    /** Se redondea hacia abajo: «79,9 %» no es «80 %», y la alerta no puede saltar antes de tiempo. */
    @Test void elPorcentajeSeRedondeaHaciaAbajo() {
        assertThat(UsoDeLimite.porcentaje(239, Limite.de(300))).hasValue(79);
        assertThat(UsoDeLimite.porcentaje(240, Limite.de(300))).hasValue(80);
        assertThat(UsoDeLimite.porcentaje(1, Limite.de(3))).hasValue(33);
    }

    @Test void pasarseDelTopeSigueSiendoUnPorcentajeMayorACien() {
        assertThat(UsoDeLimite.porcentaje(450, Limite.de(300))).hasValue(150);
        assertThat(UsoDeLimite.porcentaje(1, Limite.de(1))).hasValue(100);
        assertThat(UsoDeLimite.porcentaje(2, Limite.de(1))).hasValue(200);
    }

    @Test void sinTopeNoHayPorcentaje() {
        assertThat(UsoDeLimite.porcentaje(999_999, Limite.sinLimite())).isEmpty();
    }

    @Test void noSeDesbordaConCifrasGrandes() {
        assertThat(UsoDeLimite.porcentaje(Long.MAX_VALUE / 200, Limite.de(Integer.MAX_VALUE))).isPresent();
        assertThat(UsoDeLimite.porcentaje(4_000_000_000L, Limite.de(2_000_000_000))).hasValue(200);
    }

    @Test void laAlertaEmpiezaEnElOchentaPorCientoInclusive() {
        assertThat(UsoDeLimite.UMBRAL_DE_ALERTA).isEqualTo(80);
        assertThat(UsoDeLimite.enAlerta(239, Limite.de(300))).isFalse();
        assertThat(UsoDeLimite.enAlerta(240, Limite.de(300))).isTrue();
        assertThat(UsoDeLimite.enAlerta(241, Limite.de(300))).isTrue();
        assertThat(UsoDeLimite.enAlerta(300, Limite.de(300))).isTrue();
        assertThat(UsoDeLimite.enAlerta(900, Limite.de(300))).isTrue();
    }

    @Test void unTopeChicoTambienAlertaEnSuMomento() {
        assertThat(UsoDeLimite.enAlerta(3, Limite.de(5))).isFalse();
        assertThat(UsoDeLimite.enAlerta(4, Limite.de(5))).isTrue();
        assertThat(UsoDeLimite.enAlerta(0, Limite.de(1))).isFalse();
        assertThat(UsoDeLimite.enAlerta(1, Limite.de(1))).isTrue();
    }

    @Test void sinTopeNuncaAlerta() {
        assertThat(UsoDeLimite.enAlerta(0, Limite.sinLimite())).isFalse();
        assertThat(UsoDeLimite.enAlerta(10_000_000, Limite.sinLimite())).isFalse();
    }

    /** La alerta y el porcentaje no pueden contradecirse: alerta si y solo si el porcentaje llega al umbral. */
    @Test void laAlertaYElPorcentajeConcuerdan() {
        for (int tope : new int[]{1, 3, 7, 30, 300, 1500}) {
            for (long docs = 0; docs <= tope * 2L; docs++) {
                boolean alerta = UsoDeLimite.enAlerta(docs, Limite.de(tope));
                int pct = UsoDeLimite.porcentaje(docs, Limite.de(tope)).orElseThrow();
                assertThat(alerta).as("%d de %d (%d %%)", docs, tope, pct).isEqualTo(pct >= UsoDeLimite.UMBRAL_DE_ALERTA);
            }
        }
    }
}
