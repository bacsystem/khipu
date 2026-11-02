package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    /**
     * Un cambio en el mismo instante en que nace la cuenta (el alta con un plan elegido): las dos empiezan a la vez y, en la misma transacción, también tienen el
     * mismo {@code created_at}. La que terminó va primero sin depender de ese desempate; aquí la cerrada se fuerza a parecer más nueva para que lo demuestre.
     */
    @Test void aIgualInicioLaQueTerminoVaAntesQueLaActiva() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta p = repo.deLaCuenta(c).orElseThrow();
        PlanesDeCuenta p2 = p.cambiarA(UUID.randomUUID(), plan("Pro"), T0, null, 0);
        repo.cambiar(p.activa(), p2.activa());
        jdbc.update("UPDATE suscripcion SET created_at = now() + interval '1 hour' WHERE cuenta_id = ? AND termina_en IS NOT NULL", c);

        assertThat(repo.deLaCuenta(c).orElseThrow().suscripciones()).extracting(Suscripcion::planId).containsExactly(plan("Gratis"), plan("Pro"));
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

    // --- el cambio programado (#191) --------------------------------------------------------------------------------------------------------

    static final Instant CICLO = Instant.parse("2026-11-01T05:00:00Z");

    CambioDePlan aEmprende() { return new CambioDePlan(plan("Emprende"), CICLO, CICLO.plus(Duration.ofDays(30)), 3); }

    long programados(UUID cuenta) { return jdbc.queryForObject("SELECT count(*) FROM suscripcion_cambio_programado WHERE cuenta_id = ?", Long.class, cuenta); }

    @Test void unaCuentaNuevaNoTieneNadaProgramado() {
        assertThat(repo.deLaCuenta(cuenta("ana@negocio.pe")).orElseThrow().programado()).isNull();
    }

    @Test void programarGuardaElCambioConSuVencimientoYSuGraciaYNoTocaLaSuscripcionActiva() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta antes = repo.deLaCuenta(c).orElseThrow();

        repo.programar(c, aEmprende());

        PlanesDeCuenta leido = repo.deLaCuenta(c).orElseThrow();
        assertThat(leido.programado()).isEqualTo(aEmprende());
        assertThat(leido.activa()).isEqualTo(antes.activa());
        assertThat(leido.suscripciones()).hasSize(1);
        assertThat(activas(c)).isEqualTo(1);
    }

    @Test void unCambioSinVencimientoNiGraciaTambienSeGuarda() {
        UUID c = cuenta("ana@negocio.pe");

        repo.programar(c, new CambioDePlan(plan("Gratis"), CICLO, null, 0));

        assertThat(repo.deLaCuenta(c).orElseThrow().programado()).isEqualTo(new CambioDePlan(plan("Gratis"), CICLO, null, 0));
    }

    @Test void unSegundoCambioProgramadoReemplazaAlPrimero() {
        UUID c = cuenta("ana@negocio.pe");
        repo.programar(c, aEmprende());

        repo.programar(c, new CambioDePlan(plan("Gratis"), CICLO.plusSeconds(60), null, 0));

        assertThat(programados(c)).isEqualTo(1);
        CambioDePlan leido = repo.deLaCuenta(c).orElseThrow().programado();
        assertThat(leido.planId()).isEqualTo(plan("Gratis"));
        assertThat(leido.aplicaDesde()).isEqualTo(CICLO.plusSeconds(60));
        assertThat(leido.venceEn()).isNull();
        assertThat(leido.diasDeGracia()).isZero();
    }

    @Test void cancelarLoProgramadoLoBorraYSinNadaNoHaceNada() {
        UUID c = cuenta("ana@negocio.pe");
        repo.programar(c, aEmprende());

        repo.cancelarProgramado(c);
        repo.cancelarProgramado(c);

        assertThat(programados(c)).isZero();
        assertThat(repo.deLaCuenta(c).orElseThrow().programado()).isNull();
    }

    @Test void loProgramadoDeUnaCuentaNoApareceEnOtra() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");

        repo.programar(a, aEmprende());

        assertThat(repo.deLaCuenta(b).orElseThrow().programado()).isNull();
        repo.cancelarProgramado(b);
        assertThat(repo.deLaCuenta(a).orElseThrow().programado()).isNotNull();
    }

    @Test void noSeProgramaUnPlanOUnaCuentaQueNoExisten() {
        UUID c = cuenta("ana@negocio.pe");

        assertThatThrownBy(() -> repo.programar(c, new CambioDePlan(UUID.randomUUID(), CICLO, null, 0))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repo.programar(UUID.randomUUID(), aEmprende())).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Un cambio inmediato (una subida, una renovación) deja sin efecto la bajada que esperaba: en la misma sentencia que cierra la suscripción. */
    @Test void cambiarDePlanAhoraCancelaLoProgramado() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta antes = repo.deLaCuenta(c).orElseThrow();
        repo.programar(c, aEmprende());
        PlanesDeCuenta despues = antes.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(60), null, 0);

        assertThat(repo.cambiar(antes.activa(), despues.activa())).isTrue();

        assertThat(programados(c)).isZero();
        assertThat(repo.deLaCuenta(c).orElseThrow().activa().planId()).isEqualTo(plan("Pro"));
    }

    /** Si el cambio no se hizo (alguien ya había cambiado la activa), lo programado de la cuenta se queda como estaba. */
    @Test void unCambioQueNoSeHizoNoBorraLoProgramado() {
        UUID c = cuenta("ana@negocio.pe");
        PlanesDeCuenta antes = repo.deLaCuenta(c).orElseThrow();
        PlanesDeCuenta unoMas = antes.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(60), null, 0);
        repo.cambiar(antes.activa(), unoMas.activa());
        repo.programar(c, aEmprende());

        assertThat(repo.cambiar(antes.activa(), antes.cambiarA(UUID.randomUUID(), plan("Negocio"), T0.plusSeconds(120), null, 0).activa())).isFalse();

        assertThat(programados(c)).isEqualTo(1);
    }

    @Test void cambiarLaSuscripcionDeUnaCuentaNoBorraLoProgramadoDeOtra() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");
        repo.programar(b, aEmprende());
        PlanesDeCuenta pa = repo.deLaCuenta(a).orElseThrow();

        repo.cambiar(pa.activa(), pa.cambiarA(UUID.randomUUID(), plan("Pro"), T0.plusSeconds(60), null, 0).activa());

        assertThat(programados(b)).isEqualTo(1);
    }

    @Test void lasCuentasConUnCambioVencidoSonLasQueYaLlegaronASuFechaPorOrden() {
        UUID tarde = cuenta("tarde@negocio.pe");
        UUID pronto = cuenta("pronto@negocio.pe");
        UUID futura = cuenta("futura@negocio.pe");
        UUID sinNada = cuenta("nada@negocio.pe");
        repo.programar(tarde, new CambioDePlan(plan("Emprende"), CICLO.plusSeconds(3600), null, 0));
        repo.programar(pronto, new CambioDePlan(plan("Emprende"), CICLO, null, 0));
        repo.programar(futura, new CambioDePlan(plan("Emprende"), CICLO.plusSeconds(999_999), null, 0));

        List<UUID> vencidos = repo.cuentasConCambioVencido(CICLO.plusSeconds(7200), 100);

        assertThat(vencidos).containsExactly(pronto, tarde);
        assertThat(vencidos).doesNotContain(futura, sinNada);
    }

    @Test void enElInstanteExactoDeLaFechaYaEstaVencido() {
        UUID c = cuenta("ana@negocio.pe");
        repo.programar(c, aEmprende());

        assertThat(repo.cuentasConCambioVencido(CICLO.minusMillis(1), 100)).isEmpty();
        assertThat(repo.cuentasConCambioVencido(CICLO, 100)).containsExactly(c);
    }

    @Test void elLimiteAcotaCuantasSeTraenYSiempreLasMasViejas() {
        UUID a = cuenta("a@negocio.pe");
        UUID b = cuenta("b@negocio.pe");
        UUID c = cuenta("c@negocio.pe");
        repo.programar(a, new CambioDePlan(plan("Emprende"), CICLO.plusSeconds(30), null, 0));
        repo.programar(b, new CambioDePlan(plan("Emprende"), CICLO.plusSeconds(10), null, 0));
        repo.programar(c, new CambioDePlan(plan("Emprende"), CICLO.plusSeconds(20), null, 0));

        assertThat(repo.cuentasConCambioVencido(CICLO.plusSeconds(60), 2)).containsExactly(b, c);
    }

    /** Aplicar lo programado con el agregado y guardarlo: la actual termina en la fecha programada, la nueva empieza ahí y lo programado desaparece. */
    @Test void aplicarLoProgramadoDejaElHistorialConLaFechaDelCambio() {
        UUID c = cuenta("ana@negocio.pe");
        repo.programar(c, aEmprende());
        PlanesDeCuenta leida = repo.deLaCuenta(c).orElseThrow();
        PlanesDeCuenta aplicada = leida.aplicarProgramadoEn(CICLO.plusSeconds(7200), UUID.randomUUID());

        assertThat(repo.cambiar(leida.activa(), aplicada.activa())).isTrue();

        PlanesDeCuenta resultado = repo.deLaCuenta(c).orElseThrow();
        assertThat(resultado.programado()).isNull();
        assertThat(resultado.suscripciones().get(0).terminaEn()).isEqualTo(CICLO);
        assertThat(resultado.activa().planId()).isEqualTo(plan("Emprende"));
        assertThat(resultado.activa().iniciaEn()).isEqualTo(CICLO);
        assertThat(resultado.activa().venceEn()).isEqualTo(CICLO.plus(Duration.ofDays(30)));
        assertThat(resultado.activa().diasDeGracia()).isEqualTo(3);
        assertThat(activas(c)).isEqualTo(1);
    }
}
