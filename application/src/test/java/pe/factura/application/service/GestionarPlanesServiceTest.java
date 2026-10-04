package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.GestionarPlanesUseCase.DatosDePlan;
import pe.factura.application.port.in.GestionarPlanesUseCase.PlanConUso;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDeLimites;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Gestión de planes (#190): lo que importa es que cambiar un límite no toca el ciclo en curso, que desactivar no toca a las cuentas, que un plan en uso no se borra y que
 * todo cambio deja su registro en la misma transacción. {@code Fakes.CLOCK} es el 13 de septiembre de 2026; el ciclo siguiente empieza el 1 de octubre a las 05:00Z.
 */
class GestionarPlanesServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Instant PROXIMO_CICLO = Instant.parse("2026-10-01T05:00:00Z");
    static final Limites LIMITES = new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5);

    /** En memoria, con las reglas que el real garantiza en la base: nombre único sin mayúsculas, borrar solo si nunca se usó y no es el por defecto. */
    static class Planes implements PlanRepository {
        final Map<UUID, Plan> datos = new LinkedHashMap<>();
        final Map<UUID, Long> vigentes = new HashMap<>();
        final Map<UUID, Long> historial = new HashMap<>();
        final List<String> llamadas = new ArrayList<>();
        int guardados;
        public Optional<Plan> buscar(UUID id) { llamadas.add("buscar"); return Optional.ofNullable(datos.get(id)); }
        public Optional<Plan> buscarParaEditar(UUID id) { llamadas.add("buscarParaEditar"); return Optional.ofNullable(datos.get(id)); }
        public List<Plan> listar() { return datos.values().stream().sorted(Comparator.comparing(Plan::precioMensual).thenComparing(Plan::nombre)).toList(); }
        public Plan porDefecto() { return datos.values().stream().filter(Plan::porDefecto).findFirst().orElseThrow(); }
        public void guardar(Plan p) {
            if (datos.values().stream().anyMatch(o -> !o.id().equals(p.id()) && o.nombre().equalsIgnoreCase(p.nombre())))
                throw new DomainException("NOMBRE_DUPLICADO", "Ya existe un plan llamado «" + p.nombre() + "»");
            guardados++;
            datos.put(p.id(), p);
        }
        public boolean eliminar(UUID id) {
            Plan p = datos.get(id);
            if (p == null || p.porDefecto() || suscripcionesDelPlan(id) > 0) return false;
            datos.remove(id);
            return true;
        }
        public Map<UUID, Long> cuentasPorPlan() { return vigentes; }
        public long suscripcionesDelPlan(UUID id) { return historial.getOrDefault(id, 0L) + vigentes.getOrDefault(id, 0L); }
    }

    Planes planes = new Planes();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    GestionarPlanesService service = new GestionarPlanesService(planes, auditoria, uow, Fakes.CLOCK);

    Plan gratis = guardado("Gratis", "0", true);
    Plan emprende = guardado("Emprende", "29", false);

    Plan guardado(String nombre, String precio, boolean porDefecto) {
        Plan p = new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), LIMITES, EstadoPlan.ACTIVO, porDefecto);
        planes.datos.put(p.id(), p);
        planes.guardados = 0;
        return p;
    }

    static DatosDePlan datos(String nombre, String precio, Limites limites) { return new DatosDePlan(nombre, new BigDecimal(precio), limites); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    RegistroAuditoria unicoRegistro() {
        assertThat(auditoria.registros).hasSize(1);
        return auditoria.registros.get(0);
    }

    // --- listar -----------------------------------------------------------------------------------------------------------------------------

    @Test void listaLosPlanesDelMasBaratoAlMasCaroConSusCuentas() {
        planes.vigentes.put(emprende.id(), 3L);

        List<PlanConUso> lista = service.listar();

        assertThat(lista).extracting(p -> p.plan().nombre()).containsExactly("Gratis", "Emprende");
        assertThat(lista).extracting(PlanConUso::cuentas).containsExactly(0L, 3L);
    }

    @Test void unCambioDeLimitesQueYaLlegoSeListaComoVigente() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);
        planes.datos.put(emprende.id(), new Plan(emprende.id(), "Emprende", emprende.precioMensual(), LIMITES, EstadoPlan.ACTIVO, false,
                new CambioDeLimites(mas, Fakes.CLOCK.instant().minusSeconds(1))));

        Plan visto = service.listar().get(1).plan();

        assertThat(visto.limites()).isEqualTo(mas);
        assertThat(visto.programado()).isNull();
    }

    @Test void unCambioQueAunNoLlegaSeListaComoProgramado() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);
        planes.datos.put(emprende.id(), new Plan(emprende.id(), "Emprende", emprende.precioMensual(), LIMITES, EstadoPlan.ACTIVO, false, new CambioDeLimites(mas, PROXIMO_CICLO)));

        Plan visto = service.listar().get(1).plan();

        assertThat(visto.limites()).isEqualTo(LIMITES);
        assertThat(visto.programado()).isEqualTo(new CambioDeLimites(mas, PROXIMO_CICLO));
    }

    // --- crear ------------------------------------------------------------------------------------------------------------------------------

    @Test void crearDejaUnPlanActivoQueNoEsElPorDefecto() {
        PlanConUso creado = service.crear(ACTOR, datos("Estudio", "49.90", LIMITES));

        assertThat(creado.plan().nombre()).isEqualTo("Estudio");
        assertThat(creado.plan().precioMensual()).isEqualByComparingTo("49.90");
        assertThat(creado.plan().activo()).isTrue();
        assertThat(creado.plan().porDefecto()).isFalse();
        assertThat(creado.plan().programado()).isNull();
        assertThat(creado.cuentas()).isZero();
        assertThat(planes.datos).containsKey(creado.plan().id());
    }

    @Test void crearQuedaEnLaBitacoraConQuienLoHizoYSuDetalle() {
        service.crear(ACTOR, datos("Estudio", "49.90", new Limites(Limite.sinLimite(), 7, Limite.de(9), Limite.de(4), 6)));

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.CREAR_PLAN);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.cuentaId()).isNull();
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(r.detalle()).isEqualTo("plan=Estudio precio=49.90 documentos=ilimitado ruc=7 usuarios=9 api_keys=4 retencion=6");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void crearConUnNombreRepetidoSeRechazaSinDejarRegistro() {
        assertThat(codigo(() -> service.crear(ACTOR, datos("EMPRENDE", "10", LIMITES)))).isEqualTo("NOMBRE_DUPLICADO");

        assertThat(auditoria.registros).isEmpty();
    }

    @Test void crearConDatosInvalidosNoGuardaNiRegistra() {
        assertThat(codigo(() -> service.crear(ACTOR, datos("X", "-1", LIMITES)))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(() -> service.crear(ACTOR, datos(" ", "1", LIMITES)))).isEqualTo("NOMBRE_REQUERIDO");
        assertThat(codigo(() -> service.crear(ACTOR, new DatosDePlan("X", BigDecimal.ONE, null)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(() -> service.crear(ACTOR, null))).isEqualTo("DATOS_INVALIDOS");

        assertThat(planes.guardados).isZero();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void siLaBitacoraFallaElErrorSubeParaQueLaTransaccionDeshagaElPlan() {
        auditoria.falla = new IllegalStateException("bitácora caída");

        assertThatThrownBy(() -> service.crear(ACTOR, datos("Estudio", "49.90", LIMITES))).isInstanceOf(IllegalStateException.class);
    }

    // --- editar -----------------------------------------------------------------------------------------------------------------------------

    @Test void editarCambiaElNombreYElPrecioAlInstante() {
        PlanConUso editado = service.editar(ACTOR, emprende.id(), datos("Emprende Plus", "39.50", LIMITES));

        assertThat(editado.plan().nombre()).isEqualTo("Emprende Plus");
        assertThat(editado.plan().precioMensual()).isEqualByComparingTo("39.50");
        assertThat(planes.datos.get(emprende.id()).nombre()).isEqualTo("Emprende Plus");
    }

    /** Lo central del issue: subir el tope a mitad de mes no regala documentos del ciclo en curso. */
    @Test void editarLosLimitesLosDejaParaElCicloSiguienteYNoToqueLosDeAhora() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);

        PlanConUso editado = service.editar(ACTOR, emprende.id(), datos("Emprende", "29", mas));

        assertThat(editado.plan().limites()).isEqualTo(LIMITES);
        assertThat(editado.plan().programado()).isEqualTo(new CambioDeLimites(mas, PROXIMO_CICLO));
        assertThat(planes.datos.get(emprende.id()).limites()).isEqualTo(LIMITES);
    }

    @Test void bajarUnLimiteTampocoLeCortaANadieAMitadDeMes() {
        Limites menos = new Limites(Limite.de(10), 1, Limite.de(1), Limite.de(2), 5);

        PlanConUso editado = service.editar(ACTOR, emprende.id(), datos("Emprende", "29", menos));

        assertThat(editado.plan().limites().documentosAlMes()).isEqualTo(Limite.de(300));
        assertThat(editado.plan().programado().limites().documentosAlMes()).isEqualTo(Limite.de(10));
    }

    @Test void editarNoTocaALasCuentasQueTienenElPlan() {
        planes.vigentes.put(emprende.id(), 4L);

        PlanConUso editado = service.editar(ACTOR, emprende.id(), datos("Emprende", "99", LIMITES));

        assertThat(editado.cuentas()).isEqualTo(4L);
        assertThat(planes.vigentes).containsEntry(emprende.id(), 4L);
    }

    @Test void editarDejaEnLaBitacoraQueCambioDeQueAQue() {
        service.editar(ACTOR, emprende.id(), datos("Emprende Plus", "39.50", new Limites(Limite.de(900), 1, Limite.sinLimite(), Limite.de(2), 5)));

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.EDITAR_PLAN);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.detalle()).isEqualTo("plan=Emprende; nombre=Emprende>Emprende Plus; precio=29.00>39.50; documentos=300>900; usuarios=1>ilimitado; limites_desde=2026-10-01");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void editarSoloElPrecioNoMencionaLosLimites() {
        service.editar(ACTOR, emprende.id(), datos("Emprende", "35", LIMITES));

        assertThat(unicoRegistro().detalle()).isEqualTo("plan=Emprende; precio=29.00>35.00");
    }

    @Test void editarSinCambiarNadaNoEscribeNiDejaRegistro() {
        PlanConUso igual = service.editar(ACTOR, emprende.id(), datos("Emprende", "29", LIMITES));

        assertThat(igual.plan()).isEqualTo(emprende);
        assertThat(planes.guardados).isZero();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void ponerOtraVezLosLimitesVigentesCancelaElProgramadoYQuedaRegistrado() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);
        service.editar(ACTOR, emprende.id(), datos("Emprende", "29", mas));
        auditoria.registros.clear();

        PlanConUso cancelado = service.editar(ACTOR, emprende.id(), datos("Emprende", "29", LIMITES));

        assertThat(cancelado.plan().programado()).isNull();
        assertThat(unicoRegistro().detalle()).isEqualTo("plan=Emprende; limites_programados=cancelados");
    }

    @Test void editarUnPlanQueNoExisteEsNoEncontrado() {
        assertThat(codigo(() -> service.editar(ACTOR, UUID.randomUUID(), datos("X", "1", LIMITES)))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.editar(ACTOR, null, datos("X", "1", LIMITES)))).isEqualTo("NO_ENCONTRADO");
    }

    @Test void editarConUnNombreDeOtroPlanSeRechaza() {
        assertThat(codigo(() -> service.editar(ACTOR, emprende.id(), datos("gratis", "29", LIMITES)))).isEqualTo("NOMBRE_DUPLICADO");

        assertThat(auditoria.registros).isEmpty();
    }

    @Test void editarConDatosInvalidosNoCambiaNada() {
        assertThat(codigo(() -> service.editar(ACTOR, emprende.id(), datos("Emprende", "-5", LIMITES)))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(() -> service.editar(ACTOR, emprende.id(), null))).isEqualTo("DATOS_INVALIDOS");

        assertThat(planes.datos.get(emprende.id())).isEqualTo(emprende);
        assertThat(auditoria.registros).isEmpty();
    }

    /** Dos ediciones o una edición y un borrado a la vez se serializan: se lee con bloqueo, dentro de la transacción. */
    @Test void editarLeeElPlanConBloqueo() {
        service.editar(ACTOR, emprende.id(), datos("Emprende", "35", LIMITES));

        assertThat(planes.llamadas).containsExactly("buscarParaEditar");
    }

    @Test void editarUnPlanDespuesDeQueSuCambioYaEntroPartDeLosLimitesNuevos() {
        Limites mas = new Limites(Limite.de(900), 1, Limite.de(1), Limite.de(2), 5);
        planes.datos.put(emprende.id(), new Plan(emprende.id(), "Emprende", emprende.precioMensual(), LIMITES, EstadoPlan.ACTIVO, false,
                new CambioDeLimites(mas, Fakes.CLOCK.instant().minusSeconds(1))));

        PlanConUso editado = service.editar(ACTOR, emprende.id(), datos("Emprende", "40", mas));

        assertThat(editado.plan().limites()).isEqualTo(mas);
        assertThat(editado.plan().programado()).isNull();
        assertThat(planes.datos.get(emprende.id()).limites()).isEqualTo(mas);
        assertThat(unicoRegistro().detalle()).isEqualTo("plan=Emprende; precio=29.00>40.00");
    }

    // --- desactivar y activar ---------------------------------------------------------------------------------------------------------------

    /** Sale de la oferta, pero las cuentas que ya lo tienen siguen con él: su suscripción no se toca. */
    @Test void desactivarSacaElPlanDeLaOfertaSinTocarALasCuentas() {
        planes.vigentes.put(emprende.id(), 6L);

        PlanConUso desactivado = service.desactivar(ACTOR, emprende.id());

        assertThat(desactivado.plan().activo()).isFalse();
        assertThat(planes.datos.get(emprende.id()).activo()).isFalse();
        assertThat(desactivado.cuentas()).isEqualTo(6L);
        assertThat(planes.vigentes).containsEntry(emprende.id(), 6L);
    }

    @Test void desactivarQuedaEnLaBitacoraConCuantasCuentasLoSiguenTeniendo() {
        planes.vigentes.put(emprende.id(), 6L);

        service.desactivar(ACTOR, emprende.id());

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.DESACTIVAR_PLAN);
        assertThat(r.detalle()).isEqualTo("plan=Emprende cuentas=6");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void elPlanPorDefectoNoSeDesactiva() {
        assertThat(codigo(() -> service.desactivar(ACTOR, gratis.id()))).isEqualTo("PLAN_POR_DEFECTO");

        assertThat(planes.datos.get(gratis.id()).activo()).isTrue();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void desactivarUnoInactivoOActivarUnoActivoSeRechaza() {
        assertThat(codigo(() -> service.activar(ACTOR, emprende.id()))).isEqualTo("PLAN_YA_ACTIVO");
        service.desactivar(ACTOR, emprende.id());
        auditoria.registros.clear();

        assertThat(codigo(() -> service.desactivar(ACTOR, emprende.id()))).isEqualTo("PLAN_YA_INACTIVO");
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void activarLoDevuelveALaOfertaYLoRegistra() {
        service.desactivar(ACTOR, emprende.id());
        auditoria.registros.clear();

        PlanConUso activado = service.activar(ACTOR, emprende.id());

        assertThat(activado.plan().activo()).isTrue();
        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.ACTIVAR_PLAN);
        assertThat(r.detalle()).isEqualTo("plan=Emprende");
    }

    @Test void desactivarOActivarUnPlanQueNoExisteEsNoEncontrado() {
        assertThat(codigo(() -> service.desactivar(ACTOR, UUID.randomUUID()))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.activar(ACTOR, UUID.randomUUID()))).isEqualTo("NO_ENCONTRADO");
    }

    @Test void desactivarNoBorraElPlan() {
        service.desactivar(ACTOR, emprende.id());

        assertThat(planes.datos).containsKey(emprende.id());
    }

    // --- eliminar ---------------------------------------------------------------------------------------------------------------------------

    @Test void unPlanQueNadieUsoSeBorraYQuedaRegistrado() {
        service.eliminar(ACTOR, emprende.id());

        assertThat(planes.datos).doesNotContainKey(emprende.id());
        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.ELIMINAR_PLAN);
        assertThat(r.detalle()).isEqualTo("plan=Emprende");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    /** «Un plan con cuentas activas no se puede borrar, solo desactivar». */
    @Test void unPlanConCuentasNoSeBorra() {
        planes.vigentes.put(emprende.id(), 2L);

        DomainException e = catchThrowableOfType(DomainException.class, () -> service.eliminar(ACTOR, emprende.id()));

        assertThat(e.codigo()).isEqualTo("PLAN_EN_USO");
        assertThat(e.getMessage()).contains("2 cuentas").contains("desactívalo");
        assertThat(planes.datos).containsKey(emprende.id());
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unPlanConUnaSolaCuentaLoDiceEnSingular() {
        planes.vigentes.put(emprende.id(), 1L);

        assertThat(catchThrowableOfType(DomainException.class, () -> service.eliminar(ACTOR, emprende.id())).getMessage()).contains("1 cuenta:");
    }

    @Test void unPlanConSoloHistorialTampocoSeBorra() {
        planes.historial.put(emprende.id(), 3L);

        DomainException e = catchThrowableOfType(DomainException.class, () -> service.eliminar(ACTOR, emprende.id()));

        assertThat(e.codigo()).isEqualTo("PLAN_EN_USO");
        assertThat(e.getMessage()).contains("historial").contains("desactívalo");
        assertThat(planes.datos).containsKey(emprende.id());
    }

    @Test void elPlanPorDefectoNoSeBorra() {
        assertThat(codigo(() -> service.eliminar(ACTOR, gratis.id()))).isEqualTo("PLAN_POR_DEFECTO");

        assertThat(planes.datos).containsKey(gratis.id());
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void borrarUnPlanQueNoExisteEsNoEncontrado() {
        assertThat(codigo(() -> service.eliminar(ACTOR, UUID.randomUUID()))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(() -> service.eliminar(ACTOR, null))).isEqualTo("NO_ENCONTRADO");
    }

    /** Si entre mirar y borrar alguien le dio el plan a una cuenta, la base no deja borrarlo: es «en uso», no un 500. */
    @Test void siLaBaseNoDejaBorrarEsUnPlanEnUso() {
        PlanRepository carrera = new Planes() {
            { datos.putAll(planes.datos); }
            @Override public boolean eliminar(UUID id) { return false; }
        };
        GestionarPlanesService s = new GestionarPlanesService(carrera, auditoria, uow, Fakes.CLOCK);

        assertThat(codigo(() -> s.eliminar(ACTOR, emprende.id()))).isEqualTo("PLAN_EN_USO");
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void borrarLeeElPlanConBloqueo() {
        service.eliminar(ACTOR, emprende.id());

        assertThat(planes.llamadas).containsExactly("buscarParaEditar");
    }
}
