package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase.Resultado;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Efecto;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Previsualizacion;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.SuscripcionRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.DireccionDeCambio;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * El cambio de plan de una cuenta (#191): subir entra ya, bajar espera al ciclo siguiente, y siempre queda en la bitácora. {@code Fakes.CLOCK} es el 13 de septiembre
 * de 2026 (10:00 en Lima); el ciclo siguiente empieza el 1 de octubre a las 05:00Z.
 */
class CambiarPlanDeCuentaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Instant AHORA = Fakes.CLOCK.instant();
    static final Instant PROXIMO_CICLO = Instant.parse("2026-10-01T05:00:00Z");
    static final Instant VENCE = Instant.parse("2026-11-01T05:00:00Z");
    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);

    final UUID cuentaId = UUID.randomUUID();

    GestionarPlanesServiceTest.Planes planes = new GestionarPlanesServiceTest.Planes();
    Plan gratis = plan("Gratis", "0", 30, true);
    Plan emprende = plan("Emprende", "29", 300, false);
    Plan negocio = plan("Negocio", "69", 1500, false);
    Plan pro = new Plan(UUID.randomUUID(), "Pro", new BigDecimal("129"), new Limites(Limite.sinLimite(), 10, Limite.sinLimite(), Limite.sinLimite(), 5), EstadoPlan.ACTIVO, false);
    { planes.datos.put(pro.id(), pro); }

    Plan plan(String nombre, String precio, int documentos, boolean porDefecto) {
        Plan p = new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), new Limites(Limite.de(documentos), 1, Limite.de(1), Limite.de(2), 5), EstadoPlan.ACTIVO, porDefecto);
        planes.datos.put(p.id(), p);
        return p;
    }

    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { throw new AssertionError("cambiar el plan no reescribe la cuenta"); }
        public Optional<Cuenta> buscar(UUID id) { return cuentaId.equals(id) ? Optional.of(new Cuenta(cuentaId, "Mi negocio", "ana@negocio.pe")) : Optional.empty(); }
        public Optional<Cuenta> buscarPorEmail(String email) { throw new AssertionError("no se busca por correo"); }
    };

    /** En memoria, con la misma regla del real: el cambio es condicional (solo si la activa sigue siendo la que se vio) y un cambio cancela lo programado. */
    static class Suscripciones implements SuscripcionRepository {
        final Map<UUID, PlanesDeCuenta> datos = new HashMap<>();
        boolean negarCambios;
        public Optional<PlanesDeCuenta> deLaCuenta(UUID c) { return Optional.ofNullable(datos.get(c)); }
        public boolean cambiar(Suscripcion actual, Suscripcion nueva) {
            PlanesDeCuenta p = datos.get(actual.cuentaId());
            if (negarCambios || p == null || !p.activa().id().equals(actual.id())) return false;
            datos.put(actual.cuentaId(), p.cambiarA(nueva.id(), nueva.planId(), nueva.iniciaEn(), nueva.venceEn(), nueva.diasDeGracia()));
            return true;
        }
        public void programar(UUID c, CambioDePlan cambio) { datos.put(c, datos.get(c).programar(cambio)); }
        public void cancelarProgramado(UUID c) { datos.put(c, datos.get(c).cancelarProgramado()); }
        public List<UUID> cuentasConCambioVencido(Instant ahora, int limite) {
            return datos.entrySet().stream().filter(e -> e.getValue().programado() != null && !e.getValue().programado().aplicaDesde().isAfter(ahora)).map(Map.Entry::getKey).limit(limite).toList();
        }
    }

    Suscripciones suscripciones = new Suscripciones();
    { empezarEn(gratis, null); }

    /** La cuenta está en {@code plan} desde hace diez días. */
    void empezarEn(Plan plan, Instant vence) {
        suscripciones.datos.put(cuentaId, new PlanesDeCuenta(cuentaId, List.of(new Suscripcion(UUID.randomUUID(), cuentaId, plan.id(), AHORA.minus(Duration.ofDays(10)), vence, 0, null))));
    }

    long consumo = 0;
    final List<YearMonth> mesesConsultados = new ArrayList<>();
    ConsultarConsumoUseCase consumos = new ConsultarConsumoUseCase() {
        public YearMonth mesActual() { return YearMonth.of(2026, 9); }
        public ConsumoDeEmpresa deEmpresa(UUID t, YearMonth mes) { throw new AssertionError("el cambio de plan mira la cuenta"); }
        public ConsumoDeCuenta deCuenta(UUID c, YearMonth mes) { mesesConsultados.add(mes); return new ConsumoDeCuenta(c, YearMonth.of(2026, 9), consumo, List.of()); }
    };

    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    CambiarPlanDeCuentaService service = new CambiarPlanDeCuentaService(cuentas, planes, suscripciones, consumos, auditoria, uow, Fakes.CLOCK);

    PlanesDeCuenta guardado() { return suscripciones.datos.get(cuentaId); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    RegistroAuditoria unicoRegistro() {
        assertThat(auditoria.registros).hasSize(1);
        return auditoria.registros.get(0);
    }

    // --- el plan de una cuenta ---------------------------------------------------------------------------------------------------------------

    @Test void elPlanDeUnaCuentaDiceCualEsEnQueEstadoDePagoEstaYHastaCuandoCubre() {
        empezarEn(emprende, AHORA.plus(Duration.ofDays(20)));

        PlanDeCuenta p = service.plan(cuentaId);

        assertThat(p.plan()).isEqualTo(emprende);
        assertThat(p.estado()).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(p.suscripcion().venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(20)));
        assertThat(p.hastaCuandoCubre()).isEqualTo(AHORA.plus(Duration.ofDays(20)));
        assertThat(p.programado()).isNull();
    }

    @Test void unPlanVencidoSeEncuentraEnGraciaYLuegoVencido() {
        suscripciones.datos.put(cuentaId, new PlanesDeCuenta(cuentaId, List.of(new Suscripcion(UUID.randomUUID(), cuentaId, emprende.id(), AHORA.minus(Duration.ofDays(40)),
                AHORA.minus(Duration.ofDays(2)), 5, null))));
        assertThat(service.plan(cuentaId).estado()).isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(service.plan(cuentaId).hastaCuandoCubre()).isEqualTo(AHORA.plus(Duration.ofDays(3)));

        suscripciones.datos.put(cuentaId, new PlanesDeCuenta(cuentaId, List.of(new Suscripcion(UUID.randomUUID(), cuentaId, emprende.id(), AHORA.minus(Duration.ofDays(40)),
                AHORA.minus(Duration.ofDays(10)), 5, null))));
        assertThat(service.plan(cuentaId).estado()).isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test void siHayUnaBajadaEsperandoLaCuentaSigueConElPlanDeHoyYSeVeLoProgramado() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), PROXIMO_CICLO, VENCE.plus(Duration.ofDays(30)), 3));

        PlanDeCuenta p = service.plan(cuentaId);

        assertThat(p.plan()).isEqualTo(negocio);
        assertThat(p.programado().plan()).isEqualTo(emprende);
        assertThat(p.programado().cambio().aplicaDesde()).isEqualTo(PROXIMO_CICLO);
        assertThat(p.programado().cambio().diasDeGracia()).isEqualTo(3);
    }

    @Test void elPlanDeUnaCuentaQueNoExisteEsNoEncontrado() {
        assertThat(codigo(() -> service.plan(UUID.randomUUID()))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.plan(null))).isEqualTo("NO_ENCONTRADO");
    }

    /** Los límites que se ven son los que mandan hoy: un cambio de límites del plan que todavía no llegó no se adelanta. */
    @Test void elPlanDeUnaCuentaMuestraLosLimitesQueMandanHoy() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);
        planes.datos.put(emprende.id(), emprende.editar("Emprende", emprende.precioMensual(), mas, AHORA));
        empezarEn(emprende, VENCE);

        assertThat(service.plan(cuentaId).plan().limites()).isEqualTo(emprende.limites());
        assertThat(service.plan(cuentaId).plan().programado().limites()).isEqualTo(mas);
    }

    // --- previsualizar ----------------------------------------------------------------------------------------------------------------------

    @Test void subirDePlanEntraAhoraYDiceCuantoConsumioLaCuentaEsteMesFrenteAlTopeNuevo() {
        empezarEn(emprende, VENCE);
        consumo = 312;

        Previsualizacion v = service.previsualizar(cuentaId, negocio.id());

        assertThat(v.planActual()).isEqualTo(emprende);
        assertThat(v.planNuevo()).isEqualTo(negocio);
        assertThat(v.direccion()).isEqualTo(DireccionDeCambio.SUBIDA);
        assertThat(v.efecto()).isEqualTo(Efecto.INMEDIATO);
        assertThat(v.aplicaDesde()).isEqualTo(AHORA);
        assertThat(v.mes()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(v.consumoDelMes()).isEqualTo(312);
        assertThat(v.limiteDeDocumentos()).isEqualTo(Limite.de(1500));
        assertThat(v.superaElLimite()).isFalse();
    }

    @Test void bajarDePlanEsperaAlCicloSiguiente() {
        empezarEn(negocio, VENCE);
        consumo = 800;

        Previsualizacion v = service.previsualizar(cuentaId, emprende.id());

        assertThat(v.direccion()).isEqualTo(DireccionDeCambio.BAJADA);
        assertThat(v.efecto()).isEqualTo(Efecto.CICLO_SIGUIENTE);
        assertThat(v.aplicaDesde()).isEqualTo(PROXIMO_CICLO);
        assertThat(v.consumoDelMes()).isEqualTo(800);
        assertThat(v.limiteDeDocumentos()).isEqualTo(Limite.de(300));
        assertThat(v.superaElLimite()).as("este mes ya pasó el tope del plan nuevo: se avisa, aunque la bajada no lo toca").isTrue();
    }

    @Test void elMismoPlanEsUnaRenovacionInmediata() {
        empezarEn(emprende, VENCE);

        Previsualizacion v = service.previsualizar(cuentaId, emprende.id());

        assertThat(v.direccion()).isEqualTo(DireccionDeCambio.RENOVACION);
        assertThat(v.efecto()).isEqualTo(Efecto.INMEDIATO);
    }

    /** Pasar a un plan sin tope de documentos nunca supera el límite; justo en el tope tampoco. */
    @Test void elTopeSeSuperaSoloPorEncimaYLoIlimitadoNuncaSeSupera() {
        empezarEn(gratis, null);
        consumo = 300;
        assertThat(service.previsualizar(cuentaId, emprende.id()).superaElLimite()).isFalse();
        consumo = 301;
        assertThat(service.previsualizar(cuentaId, emprende.id()).superaElLimite()).isTrue();
        consumo = 999_999;
        Previsualizacion aPro = service.previsualizar(cuentaId, pro.id());
        assertThat(aPro.superaElLimite()).isFalse();
        assertThat(aPro.limiteDeDocumentos().ilimitado()).isTrue();
    }

    @Test void previsualizarMiraElConsumoDeLaCuentaEnElMesEnCursoYNoEscribeNada() {
        empezarEn(emprende, VENCE);
        PlanesDeCuenta antes = guardado();

        service.previsualizar(cuentaId, negocio.id());

        assertThat(mesesConsultados).containsExactly((YearMonth) null);
        assertThat(guardado()).isEqualTo(antes);
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void siHayUnCambioVencidoSinAplicarLaDireccionSeMideDesdeElPlanQueYaManda() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), AHORA.minusSeconds(3600), null, 0));

        Previsualizacion v = service.previsualizar(cuentaId, pro.id());

        assertThat(v.planActual()).isEqualTo(emprende);
        assertThat(v.direccion()).isEqualTo(DireccionDeCambio.SUBIDA);
    }

    @Test void previsualizarRechazaLoQueNoExisteOEstaFueraDeLaOferta() {
        assertThat(codigo(() -> service.previsualizar(UUID.randomUUID(), emprende.id()))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.previsualizar(cuentaId, UUID.randomUUID()))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.previsualizar(cuentaId, null))).isEqualTo("NO_ENCONTRADO");
        planes.datos.put(emprende.id(), emprende.desactivar());
        assertThat(codigo(() -> service.previsualizar(cuentaId, emprende.id()))).isEqualTo("PLAN_INACTIVO");
    }

    // --- cambiar: subir ---------------------------------------------------------------------------------------------------------------------

    @Test void subirDePlanEntraAhoraConSuVencimientoYSuGracia() {
        empezarEn(gratis, null);
        UUID anterior = guardado().activa().id();

        PlanDeCuenta r = service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 5);

        assertThat(r.plan()).isEqualTo(emprende);
        Suscripcion nueva = guardado().activa();
        assertThat(nueva.planId()).isEqualTo(emprende.id());
        assertThat(nueva.iniciaEn()).isEqualTo(AHORA);
        assertThat(nueva.venceEn()).isEqualTo(VENCE);
        assertThat(nueva.diasDeGracia()).isEqualTo(5);
        assertThat(guardado().suscripciones()).hasSize(2);
        assertThat(guardado().suscripciones().get(0).id()).isEqualTo(anterior);
        assertThat(guardado().suscripciones().get(0).terminaEn()).isEqualTo(AHORA);
        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.VIGENTE);
    }

    @Test void subirDePlanQuedaEnLaBitacoraConQuienDesdeCualHastaCualYCuandoEntra() {
        empezarEn(emprende, VENCE);

        service.cambiar(ACTOR, cuentaId, negocio.id(), VENCE.plus(Duration.ofDays(30)), 7);

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.CAMBIAR_PLAN);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
        assertThat(r.detalle()).isEqualTo("desde=Emprende hacia=Negocio direccion=SUBIDA efecto=INMEDIATO vence=2026-12-01 gracia=7");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void unaSubidaCancelaLaBajadaQueEsperaba() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), PROXIMO_CICLO, null, 0));

        service.cambiar(ACTOR, cuentaId, pro.id(), VENCE, 0);

        assertThat(guardado().programado()).isNull();
        assertThat(guardado().activa().planId()).isEqualTo(pro.id());
    }

    // --- cambiar: bajar ---------------------------------------------------------------------------------------------------------------------

    /** Bajar no corta a nadie lo que ya pagó: la cuenta sigue con su plan de hoy, y el cambio queda esperando el inicio del ciclo siguiente. */
    @Test void bajarDePlanQuedaProgramadoYLaCuentaSigueConSuPlanDeHoy() {
        empezarEn(negocio, VENCE);
        PlanesDeCuenta antes = guardado();

        PlanDeCuenta r = service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE.plus(Duration.ofDays(30)), 3);

        assertThat(guardado().activa()).isEqualTo(antes.activa());
        assertThat(guardado().suscripciones()).hasSize(1);
        assertThat(guardado().programado()).isEqualTo(new CambioDePlan(emprende.id(), PROXIMO_CICLO, VENCE.plus(Duration.ofDays(30)), 3));
        assertThat(r.plan()).isEqualTo(negocio);
        assertThat(r.programado().plan()).isEqualTo(emprende);
    }

    @Test void bajarDePlanQuedaEnLaBitacoraConLaFechaEnQueEntra() {
        empezarEn(negocio, VENCE);

        service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE.plus(Duration.ofDays(30)), 3);

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.CAMBIAR_PLAN);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.detalle()).isEqualTo("desde=Negocio hacia=Emprende direccion=BAJADA efecto=CICLO_SIGUIENTE aplica_desde=2026-10-01 vence=2026-12-01 gracia=3");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void unaSegundaBajadaReemplazaALaPrimera() {
        empezarEn(pro, VENCE);
        service.cambiar(ACTOR, cuentaId, negocio.id(), VENCE.plus(Duration.ofDays(30)), 0);

        service.cambiar(ACTOR, cuentaId, gratis.id(), null, 0);

        assertThat(guardado().programado().planId()).isEqualTo(gratis.id());
        assertThat(guardado().programado().venceEn()).isNull();
        assertThat(guardado().activa().planId()).isEqualTo(pro.id());
        assertThat(auditoria.registros).hasSize(2);
    }

    @Test void bajarAUnPlanGratisNoNecesitaVencimiento() {
        empezarEn(emprende, VENCE);

        service.cambiar(ACTOR, cuentaId, gratis.id(), null, 0);

        assertThat(guardado().programado().venceEn()).isNull();
        assertThat(unicoRegistro().detalle()).contains("vence=sin_vencimiento");
    }

    // --- cambiar: renovar -------------------------------------------------------------------------------------------------------------------

    @Test void elMismoPlanConOtroVencimientoEntraAhoraYCancelaLaBajadaProgramada() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), PROXIMO_CICLO, null, 0));
        Instant otroVencimiento = VENCE.plus(Duration.ofDays(31));

        service.cambiar(ACTOR, cuentaId, negocio.id(), otroVencimiento, 2);

        assertThat(guardado().programado()).isNull();
        assertThat(guardado().activa().planId()).isEqualTo(negocio.id());
        assertThat(guardado().activa().venceEn()).isEqualTo(otroVencimiento);
        assertThat(guardado().activa().iniciaEn()).isEqualTo(AHORA);
        assertThat(unicoRegistro().detalle()).startsWith("desde=Negocio hacia=Negocio direccion=RENOVACION efecto=INMEDIATO");
    }

    // --- cambiar: lo que se valida ----------------------------------------------------------------------------------------------------------

    @Test void unPlanDePagoExigeVencimientoYUnoGratisNo() {
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), null, 0))).isEqualTo("VENCIMIENTO_REQUERIDO");
        assertThat(guardado().activa().planId()).isEqualTo(gratis.id());

        service.cambiar(ACTOR, cuentaId, gratis.id(), null, 0);

        assertThat(guardado().activa().venceEn()).isNull();
    }

    @Test void elVencimientoDebeSerPosteriorAlInicio() {
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), AHORA, 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), AHORA.minusSeconds(1), 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        empezarEn(negocio, VENCE);
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), PROXIMO_CICLO, 0))).as("una bajada vence después de que entra").isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");

        assertThat(auditoria.registros).isEmpty();
    }

    @Test void laGraciaVaDeCeroANoventaYSinIndicarEsCero() {
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, -1))).isEqualTo("GRACIA_INVALIDA");
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 91))).isEqualTo("GRACIA_INVALIDA");

        service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 90);
        assertThat(guardado().activa().diasDeGracia()).isEqualTo(90);

        service.cambiar(ACTOR, cuentaId, negocio.id(), VENCE, null);
        assertThat(guardado().activa().diasDeGracia()).isZero();
    }

    @Test void unPlanFueraDeLaOfertaNoSeAsignaAunqueSigaExistiendo() {
        planes.datos.put(emprende.id(), emprende.desactivar());

        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 0))).isEqualTo("PLAN_INACTIVO");

        assertThat(auditoria.registros).isEmpty();
        assertThat(guardado().activa().planId()).isEqualTo(gratis.id());
    }

    @Test void unaCuentaOUnPlanQueNoExistenSonNoEncontrados() {
        assertThat(codigo(() -> service.cambiar(ACTOR, UUID.randomUUID(), emprende.id(), VENCE, 0))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, UUID.randomUUID(), VENCE, 0))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.cambiar(ACTOR, null, emprende.id(), VENCE, 0))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, null, VENCE, 0))).isEqualTo("NO_ENCONTRADO");
        assertThat(auditoria.registros).isEmpty();
    }

    /** Dos administradores cambian a la vez: el segundo ve que la suscripción que miraba ya no es la activa y no pisa nada ni deja registro. */
    @Test void siOtroAdministradorCambioElPlanEnElMedioEsUnConflictoSinRegistro() {
        suscripciones.negarCambios = true;

        assertThat(codigo(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 0))).isEqualTo("CAMBIO_CONCURRENTE");

        assertThat(auditoria.registros).isEmpty();
        assertThat(guardado().activa().planId()).isEqualTo(gratis.id());
    }

    @Test void siLaBitacoraFallaElErrorSubeParaQueLaTransaccionDeshagaElCambio() {
        auditoria.falla = new IllegalStateException("bitácora caída");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test void siHayUnCambioVencidoSinAplicarSeAplicaAntesYLaDireccionSeMideDesdeElPlanNuevo() {
        empezarEn(negocio, VENCE);
        Instant fechaDelCambio = AHORA.minusSeconds(3600);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), fechaDelCambio, VENCE, 0));

        service.cambiar(ACTOR, cuentaId, pro.id(), VENCE, 0);

        assertThat(guardado().suscripciones()).hasSize(3);
        assertThat(guardado().suscripciones().get(0).terminaEn()).as("la de Negocio terminó cuando entró el cambio, no hoy").isEqualTo(fechaDelCambio);
        assertThat(guardado().suscripciones().get(1).planId()).isEqualTo(emprende.id());
        assertThat(guardado().activa().planId()).isEqualTo(pro.id());
        assertThat(unicoRegistro().detalle()).startsWith("desde=Emprende hacia=Pro direccion=SUBIDA");
    }

    @Test void cambiarElPlanDeUnaCuentaNoTocaLasDeOtra() {
        UUID otra = UUID.randomUUID();
        suscripciones.datos.put(otra, new PlanesDeCuenta(otra, List.of(new Suscripcion(UUID.randomUUID(), otra, gratis.id(), AHORA.minus(Duration.ofDays(5)), null, 0, null))));
        PlanesDeCuenta antes = suscripciones.datos.get(otra);

        service.cambiar(ACTOR, cuentaId, emprende.id(), VENCE, 0);

        assertThat(suscripciones.datos.get(otra)).isEqualTo(antes);
    }

    // --- aplicar lo vencido -----------------------------------------------------------------------------------------------------------------

    @Test void aplicarLosVencidosConvierteLaBajadaEnLaSuscripcionActivaDesdeLaFechaProgramada() {
        empezarEn(negocio, VENCE);
        Instant fecha = AHORA.minusSeconds(7200);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), fecha, VENCE.plus(Duration.ofDays(30)), 3));

        Resultado r = service.aplicarVencidos();

        assertThat(r).isEqualTo(new Resultado(1, 0));
        assertThat(guardado().programado()).isNull();
        assertThat(guardado().activa().planId()).isEqualTo(emprende.id());
        assertThat(guardado().activa().iniciaEn()).isEqualTo(fecha);
        assertThat(guardado().activa().venceEn()).isEqualTo(VENCE.plus(Duration.ofDays(30)));
        assertThat(guardado().activa().diasDeGracia()).isEqualTo(3);
        assertThat(guardado().suscripciones().get(0).terminaEn()).isEqualTo(fecha);
    }

    @Test void aplicarLosVencidosNoTocaLoQueTodaviaNoLlego() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), PROXIMO_CICLO, null, 0));
        PlanesDeCuenta antes = guardado();

        Resultado r = service.aplicarVencidos();

        assertThat(r).isEqualTo(new Resultado(0, 0));
        assertThat(guardado()).isEqualTo(antes);
    }

    @Test void aplicarLosVencidosCuentaLosQueFallanYSigueConLasDemas() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), AHORA.minusSeconds(60), null, 0));
        UUID rota = UUID.randomUUID();
        suscripciones.datos.put(rota, new PlanesDeCuenta(rota, List.of(new Suscripcion(UUID.randomUUID(), rota, negocio.id(), AHORA.minus(Duration.ofDays(5)), null, 0, null)))
                .programar(new CambioDePlan(emprende.id(), AHORA.minusSeconds(120), null, 0)));
        Suscripciones conFalla = new Suscripciones() {
            @Override public boolean cambiar(Suscripcion actual, Suscripcion nueva) {
                if (actual.cuentaId().equals(rota)) throw new IllegalStateException("la base se cayó para esta cuenta");
                return super.cambiar(actual, nueva);
            }
        };
        conFalla.datos.putAll(suscripciones.datos);
        CambiarPlanDeCuentaService s = new CambiarPlanDeCuentaService(cuentas, planes, conFalla, consumos, auditoria, uow, Fakes.CLOCK);

        Resultado r = s.aplicarVencidos();

        assertThat(r).isEqualTo(new Resultado(1, 1));
        assertThat(conFalla.datos.get(cuentaId).activa().planId()).isEqualTo(emprende.id());
        assertThat(conFalla.datos.get(rota).programado()).as("la que falló sigue esperando para la siguiente pasada").isNotNull();
    }

    @Test void aplicarLosVencidosSinNadaProgramadoNoHaceNada() {
        assertThat(service.aplicarVencidos()).isEqualTo(new Resultado(0, 0));
    }

    /** Aplicar una bajada que ya se había decidido no es una acción de nadie: no deja otro registro (el de cuando se programó ya dice quién). */
    @Test void aplicarLosVencidosNoDejaRegistroDeBitacora() {
        empezarEn(negocio, VENCE);
        suscripciones.programar(cuentaId, new CambioDePlan(emprende.id(), AHORA.minusSeconds(60), null, 0));

        service.aplicarVencidos();

        assertThat(auditoria.registros).isEmpty();
    }
}
