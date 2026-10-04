package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Subir de plan tiene efecto inmediato; bajar, al ciclo siguiente (#191). Lo que es subir o bajar lo decide el precio. */
class DireccionDeCambioTest {
    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);

    static Plan plan(String precio) { return new Plan(UUID.randomUUID(), "P" + precio, new BigDecimal(precio), LIMITES, EstadoPlan.ACTIVO, false); }

    @Test void unPlanMasCaroEsUnaSubida() {
        assertThat(DireccionDeCambio.entre(plan("29"), plan("69"))).isEqualTo(DireccionDeCambio.SUBIDA);
        assertThat(DireccionDeCambio.entre(plan("0"), plan("0.01"))).isEqualTo(DireccionDeCambio.SUBIDA);
    }

    @Test void unPlanMasBaratoEsUnaBajada() {
        assertThat(DireccionDeCambio.entre(plan("69"), plan("29"))).isEqualTo(DireccionDeCambio.BAJADA);
        assertThat(DireccionDeCambio.entre(plan("0.01"), plan("0"))).isEqualTo(DireccionDeCambio.BAJADA);
    }

    /** El mismo plan es una renovación (otro vencimiento u otra gracia): no baja ni sube, y tiene efecto inmediato. */
    @Test void elMismoPlanEsUnaRenovacion() {
        Plan p = plan("29");

        assertThat(DireccionDeCambio.entre(p, p)).isEqualTo(DireccionDeCambio.RENOVACION);
        assertThat(DireccionDeCambio.entre(p, new Plan(p.id(), "Renombrado", new BigDecimal("39"), LIMITES, EstadoPlan.ACTIVO, false))).isEqualTo(DireccionDeCambio.RENOVACION);
    }

    /** Dos planes distintos al mismo precio: nadie paga menos, así que no es una bajada y no hace esperar al cliente. */
    @Test void aIgualPrecioDeOtroPlanNoEsUnaBajada() {
        assertThat(DireccionDeCambio.entre(plan("29"), new Plan(UUID.randomUUID(), "Otro", new BigDecimal("29"), LIMITES, EstadoPlan.ACTIVO, false))).isEqualTo(DireccionDeCambio.SUBIDA);
    }

    @Test void soloLaBajadaEsperaAlCicloSiguiente() {
        assertThat(DireccionDeCambio.SUBIDA.esInmediata()).isTrue();
        assertThat(DireccionDeCambio.RENOVACION.esInmediata()).isTrue();
        assertThat(DireccionDeCambio.BAJADA.esInmediata()).isFalse();
    }
}
