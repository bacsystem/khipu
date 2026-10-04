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
}
