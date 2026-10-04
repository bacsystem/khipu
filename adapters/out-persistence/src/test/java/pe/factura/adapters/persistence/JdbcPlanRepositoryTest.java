package pe.factura.adapters.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDeLimites;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los planes (#189, #190) con Postgres real: lo que se guarda como NULL vuelve como «sin límite», el cambio de límites programado se guarda y se lee con el plan,
 * y borrar solo es posible si nadie usó nunca el plan.
 */
class JdbcPlanRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-10-15T15:00:00Z");
    static final Limites LIMITES = new Limites(Limite.de(50), 1, Limite.de(2), Limite.de(3), 4);
    static final Limites MAS = new Limites(Limite.de(500), 1, Limite.de(2), Limite.de(3), 4);

    JdbcPlanRepository repo = new JdbcPlanRepository(jdbc);
    JdbcSuscripcionRepository suscripciones = new JdbcSuscripcionRepository(jdbc);

    @AfterEach void quitarLosPlanesDeLaPrueba() {
        jdbc.update("DELETE FROM suscripcion");
        jdbc.update("DELETE FROM plan WHERE nombre NOT IN ('Gratis', 'Emprende', 'Negocio', 'Pro')");
    }

    Plan nuevo(String nombre) { return new Plan(UUID.randomUUID(), nombre, new BigDecimal("15"), LIMITES, EstadoPlan.ACTIVO, false); }

    UUID cuenta(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(T0));
        return id;
    }

    Plan semilla(String nombre) { return repo.listar().stream().filter(p -> p.nombre().equals(nombre)).findFirst().orElseThrow(); }

    /** Pasa la cuenta a ese plan. */
    void asignar(UUID cuenta, UUID plan, Instant desde) {
        var antes = suscripciones.deLaCuenta(cuenta).orElseThrow();
        suscripciones.cambiar(antes.activa(), antes.cambiarA(UUID.randomUUID(), plan, desde, null, 0).activa());
    }

    // --- lectura (#189) ---------------------------------------------------------------------------------------------------------------------

    @Test void listaLosCuatroPlanesDelMasBaratoAlMasCaro() {
        assertThat(repo.listar()).extracting(Plan::nombre).containsExactly("Gratis", "Emprende", "Negocio", "Pro");
    }

    @Test void losLimitesFinitosVuelvenConSuCifra() {
        Plan negocio = semilla("Negocio");

        assertThat(negocio.precioMensual()).isEqualByComparingTo("69");
        assertThat(negocio.limites()).isEqualTo(new Limites(Limite.de(1500), 3, Limite.de(3), Limite.de(5), 5));
        assertThat(negocio.estado()).isEqualTo(EstadoPlan.ACTIVO);
        assertThat(negocio.porDefecto()).isFalse();
        assertThat(negocio.programado()).isNull();
    }

    @Test void proNoTieneTopeDeDocumentosNiDeUsuariosNiDeKeys() {
        Plan pro = semilla("Pro");

        assertThat(pro.limites().documentosAlMes().ilimitado()).isTrue();
        assertThat(pro.limites().usuarios().ilimitado()).isTrue();
        assertThat(pro.limites().apiKeys().ilimitado()).isTrue();
        assertThat(pro.limites().rucs()).isEqualTo(10);
    }

    @Test void elPlanPorDefectoEsGratis() {
        Plan base = repo.porDefecto();

        assertThat(base.nombre()).isEqualTo("Gratis");
        assertThat(base.porDefecto()).isTrue();
        assertThat(base.precioMensual()).isEqualByComparingTo("0");
    }

    @Test void buscaPorIdYDevuelveVacioSiNoExiste() {
        Plan emprende = semilla("Emprende");

        assertThat(repo.buscar(emprende.id())).contains(emprende);
        assertThat(repo.buscar(UUID.randomUUID())).isEmpty();
        assertThat(repo.buscarParaEditar(emprende.id())).contains(emprende);
        assertThat(repo.buscarParaEditar(UUID.randomUUID())).isEmpty();
    }

    @Test void losInactivosTambienSeListan() {
        Plan viejo = nuevo("Viejo").desactivar();
        repo.guardar(viejo);

        assertThat(repo.listar()).contains(viejo);
        assertThat(repo.buscar(viejo.id()).orElseThrow().activo()).isFalse();
    }

    @Test void aIgualPrecioOrdenaPorNombre() {
        repo.guardar(new Plan(UUID.randomUUID(), "Abeja", new BigDecimal("29"), LIMITES, EstadoPlan.ACTIVO, false));

        List<String> nombres = repo.listar().stream().map(Plan::nombre).toList();

        assertThat(nombres.indexOf("Abeja")).isLessThan(nombres.indexOf("Emprende"));
    }

    // --- escritura (#190) -------------------------------------------------------------------------------------------------------------------

    @Test void guardarUnPlanNuevoLoDejaLeerIgual() {
        Plan p = new Plan(UUID.randomUUID(), "Estudio", new BigDecimal("99.90"), new Limites(Limite.sinLimite(), 7, Limite.de(9), Limite.sinLimite(), 6), EstadoPlan.ACTIVO, false);

        repo.guardar(p);

        assertThat(repo.buscar(p.id())).contains(p);
        assertThat(repo.buscar(p.id()).orElseThrow().limites().documentosAlMes().ilimitado()).isTrue();
    }

    @Test void guardarDeNuevoActualizaElPlanYNoCreaOtro() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        int antes = repo.listar().size();

        repo.guardar(p.editar("Estudio Pro", new BigDecimal("20"), LIMITES, T0));

        assertThat(repo.listar()).hasSize(antes);
        assertThat(repo.buscar(p.id()).orElseThrow().nombre()).isEqualTo("Estudio Pro");
        assertThat(repo.buscar(p.id()).orElseThrow().precioMensual()).isEqualByComparingTo("20");
    }

    @Test void elEstadoSeGuarda() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);

        repo.guardar(p.desactivar());
        assertThat(repo.buscar(p.id()).orElseThrow().activo()).isFalse();

        repo.guardar(p.desactivar().activar());
        assertThat(repo.buscar(p.id()).orElseThrow().activo()).isTrue();
    }

    @Test void elCambioDeLimitesProgramadoSeGuardaYSeLeeConElPlan() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        Limites mas = new Limites(Limite.de(500), 2, Limite.sinLimite(), Limite.de(8), 5);

        Plan conCambio = p.editar("Estudio", new BigDecimal("15"), mas, T0);
        repo.guardar(conCambio);

        Plan leido = repo.buscar(p.id()).orElseThrow();
        assertThat(leido).isEqualTo(conCambio);
        assertThat(leido.limites()).isEqualTo(LIMITES);
        assertThat(leido.programado()).isEqualTo(new CambioDeLimites(mas, Instant.parse("2026-11-01T05:00:00Z")));
    }

    @Test void unSegundoCambioReemplazaAlProgramadoYCancelarloLoBorra() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        Limites dos = new Limites(Limite.de(900), 1, Limite.de(2), Limite.de(3), 4);
        Plan conUno = p.editar("Estudio", new BigDecimal("15"), MAS, T0);
        repo.guardar(conUno);

        Plan conDos = conUno.editar("Estudio", new BigDecimal("15"), dos, T0.plusSeconds(60));
        repo.guardar(conDos);

        assertThat(repo.buscar(p.id()).orElseThrow().programado().limites()).isEqualTo(dos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_cambio_programado WHERE plan_id = ?", Integer.class, p.id())).isEqualTo(1);

        repo.guardar(conDos.editar("Estudio", new BigDecimal("15"), LIMITES, T0.plusSeconds(120)));

        assertThat(repo.buscar(p.id()).orElseThrow().programado()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_cambio_programado WHERE plan_id = ?", Integer.class, p.id())).isZero();
    }

    /** Pasada la fecha, guardar el plan ya vigente pasa los límites nuevos a las columnas del plan y borra el pendiente. */
    @Test void guardarElPlanVigenteDespuesDelCambioLoDejaFirme() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        repo.guardar(p.editar("Estudio", new BigDecimal("15"), MAS, T0));

        repo.guardar(repo.buscar(p.id()).orElseThrow().vigenteEn(Instant.parse("2026-11-02T00:00:00Z")));

        Plan leido = repo.buscar(p.id()).orElseThrow();
        assertThat(leido.limites()).isEqualTo(MAS);
        assertThat(leido.programado()).isNull();
    }

    @Test void elCambioProgramadoDeUnPlanNoApareceEnOtro() {
        Plan a = nuevo("Alfa");
        Plan b = nuevo("Beta");
        repo.guardar(a);
        repo.guardar(b);

        repo.guardar(a.editar("Alfa", new BigDecimal("15"), MAS, T0));

        assertThat(repo.buscar(b.id()).orElseThrow().programado()).isNull();
    }

    @Test void elNombreRepetidoSeRechazaSinImportarMayusculas() {
        repo.guardar(nuevo("Estudio"));

        assertThatThrownBy(() -> repo.guardar(nuevo("ESTUDIO"))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NOMBRE_DUPLICADO");
        assertThatThrownBy(() -> repo.guardar(nuevo("emprende"))).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NOMBRE_DUPLICADO");
    }

    @Test void editarUnPlanConElNombreDeOtroSeRechazaPeroConElPropioNo() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);

        repo.guardar(p.editar("Estudio", new BigDecimal("20"), LIMITES, T0));

        assertThatThrownBy(() -> repo.guardar(p.editar("Pro", new BigDecimal("20"), LIMITES, T0)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NOMBRE_DUPLICADO");
    }

    // --- borrar -----------------------------------------------------------------------------------------------------------------------------

    @Test void unPlanQueNadieUsoSeBorra() {
        Plan p = nuevo("Estudio");

        assertThat(repo.eliminar(p.id())).isFalse();
        repo.guardar(p);
        assertThat(repo.eliminar(p.id())).isTrue();

        assertThat(repo.buscar(p.id())).isEmpty();
    }

    @Test void alBorrarElPlanSeVaSuCambioProgramado() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        repo.guardar(p.editar("Estudio", new BigDecimal("15"), MAS, T0));

        repo.eliminar(p.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_cambio_programado WHERE plan_id = ?", Integer.class, p.id())).isZero();
    }

    @Test void unPlanConUnaCuentaVigenteNoSeBorra() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        asignar(cuenta("ana@negocio.pe"), p.id(), T0.plusSeconds(10));

        assertThat(repo.eliminar(p.id())).isFalse();

        assertThat(repo.buscar(p.id())).isPresent();
    }

    /** Con la cuenta ya en otro plan, el historial sigue apuntando al plan: borrarlo lo rompería, así que tampoco se borra. */
    @Test void unPlanConHistorialDeSuscripcionesTampocoSeBorra() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        UUID c = cuenta("ana@negocio.pe");
        asignar(c, p.id(), T0.plusSeconds(10));
        asignar(c, repo.porDefecto().id(), T0.plusSeconds(20));

        assertThat(repo.eliminar(p.id())).isFalse();
        assertThat(repo.suscripcionesDelPlan(p.id())).isEqualTo(1);
        assertThat(repo.cuentasPorPlan()).doesNotContainKey(p.id());
    }

    @Test void elPlanPorDefectoNoSeBorra() {
        assertThat(repo.eliminar(repo.porDefecto().id())).isFalse();

        assertThat(repo.porDefecto().nombre()).isEqualTo("Gratis");
    }

    // --- cuántas cuentas usan cada plan -----------------------------------------------------------------------------------------------------

    @Test void cuentaLasCuentasConCadaPlanVigente() {
        Plan p = nuevo("Estudio");
        repo.guardar(p);
        cuenta("a@negocio.pe");
        cuenta("b@negocio.pe");
        asignar(cuenta("c@negocio.pe"), p.id(), T0.plusSeconds(10));

        Map<UUID, Long> usos = repo.cuentasPorPlan();

        assertThat(usos.get(repo.porDefecto().id())).isEqualTo(2L);
        assertThat(usos.get(p.id())).isEqualTo(1L);
        assertThat(usos).doesNotContainKey(semilla("Pro").id());
    }

    @Test void sinCuentasNoHayUsos() {
        assertThat(repo.cuentasPorPlan()).isEmpty();
        assertThat(repo.suscripcionesDelPlan(semilla("Pro").id())).isZero();
    }

    @Test void lasSuscripcionesSeCuentanPorPlan() {
        Plan p = nuevo("Estudio");
        Plan q = nuevo("Taller");
        repo.guardar(p);
        repo.guardar(q);
        asignar(cuenta("ana@negocio.pe"), q.id(), T0.plusSeconds(10));

        assertThat(repo.suscripcionesDelPlan(p.id())).isZero();
        assertThat(repo.suscripcionesDelPlan(q.id())).isEqualTo(1);
    }
}
