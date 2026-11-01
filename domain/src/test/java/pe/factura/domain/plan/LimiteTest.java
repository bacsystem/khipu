package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteTest {
    @Test void unLimiteConCifraEsFinito() {
        Limite l = Limite.de(300);

        assertThat(l.ilimitado()).isFalse();
        assertThat(l.maximo()).isEqualTo(300);
    }

    @Test void ilimitadoNoTieneCifra() {
        Limite l = Limite.sinLimite();

        assertThat(l.ilimitado()).isTrue();
        assertThat(l.maximo()).isNull();
    }

    /** Cero no es «sin límite» ni «nada»: un plan que no deja emitir ni un documento no se vende. Lo ilimitado se dice con {@code sinLimite()}. */
    @Test void ceroYNegativosSonInvalidos() {
        assertThatThrownBy(() -> Limite.de(0)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("LIMITE_INVALIDO");
        assertThatThrownBy(() -> Limite.de(-1)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("LIMITE_INVALIDO");
    }

    @Test void elUnoEsElMinimoValido() {
        assertThat(Limite.de(1).maximo()).isEqualTo(1);
    }

    @Test void dosLimitesIgualesSonIguales() {
        assertThat(Limite.de(5)).isEqualTo(Limite.de(5));
        assertThat(Limite.sinLimite()).isEqualTo(Limite.sinLimite());
        assertThat(Limite.de(5)).isNotEqualTo(Limite.sinLimite());
    }

    /** #498: el N-ésimo pasa y el N+1 no; pasarse del tope (un plan bajado) también es estar lleno; lo ilimitado nunca. */
    @Test void llenoConEsElBordeExactoDelTope() {
        Limite uno = Limite.de(1);
        assertThat(uno.llenoCon(0)).isFalse();
        assertThat(uno.llenoCon(1)).isTrue();
        assertThat(uno.llenoCon(2)).as("una cuenta que ya se pasó sigue llena").isTrue();
        assertThat(Limite.de(3).llenoCon(2)).isFalse();
        assertThat(Limite.sinLimite().llenoCon(1_000_000)).isFalse();
    }
}
