package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suscripciones de las cuentas (#189), con Postgres real. Lo que importa: el cambio de plan es **una sola sentencia** que cierra la activa y abre la nueva, así que
 * la cuenta nunca queda sin plan ni con dos, y de dos cambios a la vez solo uno gana.
 */
class JdbcSuscripcionRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcSuscripcionRepository repo = new JdbcSuscripcionRepository(jdbc);

    UUID cuenta(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(T0));
        return id;
    }

    UUID plan(String nombre) { return jdbc.queryForObject("SELECT id FROM plan WHERE nombre = ?", UUID.class, nombre); }

    long activas(UUID cuenta) { return jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", Long.class, cuenta); }

    @Test void unaCuentaRecienCreadaTieneElPlanGratisComoActiva() {
        UUID c = cuenta("ana@negocio.pe");

        PlanesDeCuenta p = repo.deLaCuenta(c).orElseThrow();

        assertThat(p.cuentaId()).isEqualTo(c);
        assertThat(p.activa().planId()).isEqualTo(plan("Gratis"));
        assertThat(p.activa().iniciaEn()).isEqualTo(T0);
        assertThat(p.activa().venceEn()).isNull();
        assertThat(p.activa().diasDeGracia()).isZero();
        assertThat(p.suscripciones()).hasSize(1);
    }

    @Test void unaCuentaQueNoExisteNoTieneSuscripciones() {
        assertThat(repo.deLaCuenta(UUID.randomUUID())).isEmpty();
    }

    @Test void cambiarDePlanCierraLaActivaYAbreLaNuevaConSusFechas() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta antes = repo.deLaCuenta(c).orElseThrow();
        Instant desde = T0.plusSeconds(3600);
        Instant vence = desde.plusSeconds(30 * 86400L);
        PlanesDeCuenta despues = antes.cambiarA(UUID.randomUUID(), plan("Negocio"), desde, vence, 7);

        boolean cambio = repo.cambiar(antes.activa(), despues.activa());

        assertThat(cambio).isTrue();
        PlanesDeCuenta leido = repo.deLaCuenta(c).orElseThrow();
        assertThat(leido).isEqualTo(despues);
        assertThat(leido.activa().planId()).isEqualTo(plan("Negocio"));
        assertThat(leido.activa().venceEn()).isEqualTo(vence);
        assertThat(leido.activa().diasDeGracia()).isEqualTo(7);
        assertThat(leido.suscripciones().get(0).terminaEn()).isEqualTo(desde);
        assertThat(activas(c)).isEqualTo(1);
    }

    @Test void elHistorialVieneEnOrdenCronologico() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta p = repo.deLaCuenta(c).orElseThrow();
        PlanesDeCuenta p2 = p.cambiarA(UUID.randomUUID(), plan("Emprende"), T0.plusSeconds(100), null, 0);
        repo.cambiar(p.activa(), p2.activa());
        PlanesDeCuenta p3 = p2.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(200), null, 0);
        repo.cambiar(p2.activa(), p3.activa());

        PlanesDeCuenta leido = repo.deLaCuenta(c).orElseThrow();

        assertThat(leido.suscripciones()).extracting(Suscripcion::planId).containsExactly(plan("Gratis"), plan("Emprende"), plan("Pro"));
        assertThat(leido).isEqualTo(p3);
    }

    /** Dos administradores cambian a la vez: el segundo ve que la suscripción que miraba ya no es la activa y no cambia nada. */
    @Test void siAlguienYaCambioLaActivaElSegundoCambioNoHaceNada() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta p = repo.deLaCuenta(c).orElseThrow();
        PlanesDeCuenta aEmprende = p.cambiarA(UUID.randomUUID(), plan("Emprende"), T0.plusSeconds(10), null, 0);
        PlanesDeCuenta aPro = p.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(20), null, 0);

        assertThat(repo.cambiar(p.activa(), aEmprende.activa())).isTrue();
        assertThat(repo.cambiar(p.activa(), aPro.activa())).isFalse();

        PlanesDeCuenta leido = repo.deLaCuenta(c).orElseThrow();
        assertThat(leido.activa().planId()).isEqualTo(plan("Emprende"));
        assertThat(leido.suscripciones()).hasSize(2);
        assertThat(activas(c)).isEqualTo(1);
    }

    /** Si la nueva no se puede abrir, la vieja sigue activa: nunca queda la cuenta sin plan a medio camino. */
    @Test void siLaNuevaFallaLaCuentaConservaSuPlan() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta p = repo.deLaCuenta(c).orElseThrow();
        Suscripcion aUnPlanQueNoExiste = new Suscripcion(UUID.randomUUID(), c, UUID.randomUUID(), T0.plusSeconds(10), null, 0, null);

        assertThatThrownBy(() -> repo.cambiar(p.activa(), aUnPlanQueNoExiste)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repo.deLaCuenta(c).orElseThrow()).isEqualTo(p);
        assertThat(activas(c)).isEqualTo(1);
    }

    @Test void cambiarUnaCuentaNoTocaALasDemas() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");
        PlanesDeCuenta pa = repo.deLaCuenta(a).orElseThrow();
        PlanesDeCuenta pb = repo.deLaCuenta(b).orElseThrow();

        repo.cambiar(pa.activa(), pa.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(10), null, 0).activa());

        assertThat(repo.deLaCuenta(b).orElseThrow()).isEqualTo(pb);
    }

    /** La nueva tiene que ser de la misma cuenta que la activa: no se puede «mover» una suscripción a otra cuenta por un cambio mal armado. */
    @Test void unaNuevaDeOtraCuentaNoSeAcepta() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");
        PlanesDeCuenta pa = repo.deLaCuenta(a).orElseThrow();
        Suscripcion deB = new Suscripcion(UUID.randomUUID(), b, plan("Pro"), T0.plusSeconds(10), null, 0, null);

        assertThat(repo.cambiar(pa.activa(), deB)).isFalse();

        assertThat(repo.deLaCuenta(a).orElseThrow()).isEqualTo(pa);
        assertThat(activas(b)).isEqualTo(1);
    }
}
