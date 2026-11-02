package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ConsultarPagosUseCase.Pagina;
import pe.factura.application.port.in.RegistrarPagoUseCase.Comando;
import pe.factura.application.port.out.PagoRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.MedioDePago;
import pe.factura.domain.plan.Pago;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Registrar a mano un pago y extender, si se pide, el vencimiento (#194). {@code Fakes.CLOCK} es el 13 de septiembre de 2026 (10:00 en Lima): «pagado hasta el 30 de
 * septiembre» es la medianoche del 1 de octubre en Lima, las 05:00Z.
 */
class PagosDeCuentaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final Instant AHORA = Fakes.CLOCK.instant();
    static final Instant VENCE_1_OCT = Instant.parse("2026-10-01T05:00:00Z");
    static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    /** En memoria, con la misma regla del real: una referencia repetida (cuenta, medio, sin distinguir mayúsculas) no se guarda. */
    static class Pagos implements PagoRepository {
        final List<Pago> datos = new ArrayList<>();
        final List<String> consultas = new ArrayList<>();
        public boolean registrar(Pago p) {
            if (p.referencia() != null && datos.stream().anyMatch(o -> o.cuentaId().equals(p.cuentaId()) && o.medio() == p.medio() && p.referencia().equalsIgnoreCase(o.referencia()))) return false;
            datos.add(p);
            return true;
        }
        public List<Pago> deLaCuenta(UUID c, int pagina, int porPagina) { consultas.add(pagina + "/" + porPagina); return datos.stream().filter(p -> p.cuentaId().equals(c)).toList(); }
        public long contarDeLaCuenta(UUID c) { return datos.stream().filter(p -> p.cuentaId().equals(c)).count(); }
    }

    final UUID cuentaId = UUID.randomUUID();
    Plan emprende = new Plan(UUID.randomUUID(), "Emprende", new BigDecimal("29"), new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5), EstadoPlan.ACTIVO, false);
    Plan gratis = new Plan(UUID.randomUUID(), "Gratis", BigDecimal.ZERO, new Limites(Limite.de(30), 1, Limite.de(1), Limite.de(1), 1), EstadoPlan.ACTIVO, true);

    CambiarPlanDeCuentaServiceTest.Suscripciones suscripciones = new CambiarPlanDeCuentaServiceTest.Suscripciones();
    Pagos pagos = new Pagos();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }
    PagosDeCuentaService service = new PagosDeCuentaService(pagos, suscripciones, auditoria, uow, Fakes.CLOCK);

    Suscripcion activa;

    /** La cuenta está en {@code plan} desde hace diez días, pagada hasta {@code vence} (nulo: no vence). */
    void empezarEn(Plan plan, Instant vence, int gracia) {
        activa = new Suscripcion(UUID.randomUUID(), cuentaId, plan.id(), AHORA.minus(Duration.ofDays(10)), vence, gracia, null);
        suscripciones.datos.put(cuentaId, new PlanesDeCuenta(cuentaId, List.of(activa)));
    }

    { empezarEn(emprende, AHORA.plus(Duration.ofDays(5)), 3); }

    Suscripcion guardada() { return suscripciones.datos.get(cuentaId).activa(); }

    Comando comando(boolean extender) { return new Comando(DESDE, HASTA, new BigDecimal("29.00"), MedioDePago.YAPE, LocalDate.of(2026, 9, 12), "OP-123", "Pagó por Yape", extender); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    RegistroAuditoria unicoRegistro() {
        assertThat(auditoria.registros).hasSize(1);
        return auditoria.registros.get(0);
    }

    // --- registrar sin extender -------------------------------------------------------------------------------------------------------------

    @Test void registrarGuardaElPagoConLaSuscripcionVigenteYElInstanteDeRegistro() {
        Pago p = service.registrar(ACTOR, cuentaId, comando(false));

        assertThat(pagos.datos).containsExactly(p);
        assertThat(p.cuentaId()).isEqualTo(cuentaId);
        assertThat(p.suscripcionId()).isEqualTo(activa.id());
        assertThat(p.periodoDesde()).isEqualTo(DESDE);
        assertThat(p.periodoHasta()).isEqualTo(HASTA);
        assertThat(p.monto()).isEqualByComparingTo("29.00");
        assertThat(p.medio()).isEqualTo(MedioDePago.YAPE);
        assertThat(p.fechaDePago()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(p.referencia()).isEqualTo("OP-123");
        assertThat(p.nota()).isEqualTo("Pagó por Yape");
        assertThat(p.registradoEn()).isEqualTo(AHORA);
        assertThat(p.extendioHasta()).isNull();
    }

    @Test void sinPedirloElPagoNoMueveElVencimientoNiNadaDeLaSuscripcion() {
        service.registrar(ACTOR, cuentaId, comando(false));

        assertThat(guardada()).isEqualTo(activa);
    }

    @Test void cadaPagoTieneSuPropioId() {
        Pago a = service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 12), null, null, false));
        Pago b = service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 12), null, null, false));

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(pagos.datos).hasSize(2);
    }

    // --- extender el vencimiento ------------------------------------------------------------------------------------------------------------

    @Test void extenderPoneElVencimientoEnLaMedianocheDeLimaDelDiaSiguienteAlFinDelPeriodo() {
        Pago p = service.registrar(ACTOR, cuentaId, comando(true));

        assertThat(p.extendioHasta()).isEqualTo(VENCE_1_OCT);
        assertThat(guardada().venceEn()).isEqualTo(VENCE_1_OCT);
        assertThat(pagos.datos.get(0).extendioHasta()).isEqualTo(VENCE_1_OCT);
    }

    @Test void extenderNoCambiaElPlanLaGraciaNiLaSuscripcion() {
        service.registrar(ACTOR, cuentaId, comando(true));

        Suscripcion despues = guardada();
        assertThat(despues.id()).isEqualTo(activa.id());
        assertThat(despues.planId()).isEqualTo(activa.planId());
        assertThat(despues.diasDeGracia()).isEqualTo(3);
        assertThat(despues.iniciaEn()).isEqualTo(activa.iniciaEn());
        assertThat(despues.terminaEn()).isNull();
    }

    @Test void extenderNoTocaElCambioDePlanProgramado() {
        suscripciones.programar(cuentaId, new CambioDePlan(gratis.id(), Instant.parse("2026-10-01T05:00:00Z"), null, 0));

        service.registrar(ACTOR, cuentaId, comando(true));

        assertThat(suscripciones.datos.get(cuentaId).programado()).isNotNull();
        assertThat(suscripciones.datos.get(cuentaId).programado().planId()).isEqualTo(gratis.id());
    }

    @Test void extenderUnPlanVencidoEnGraciaLoDejaAlDia() {
        empezarEn(emprende, AHORA.minus(Duration.ofDays(2)), 5);

        service.registrar(ACTOR, cuentaId, comando(true));

        assertThat(guardada().estadoEn(AHORA).name()).isEqualTo("VIGENTE");
    }

    @Test void unPlanSinVencimientoNoTieneNadaQueExtender() {
        empezarEn(gratis, null, 0);

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, comando(true)))).isEqualTo("PLAN_SIN_VENCIMIENTO");

        assertThat(pagos.datos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
        assertThat(guardada().venceEn()).isNull();
    }

    @Test void unPlanSinVencimientoSiPuedeAnotarUnPagoSinExtender() {
        empezarEn(gratis, null, 0);

        service.registrar(ACTOR, cuentaId, comando(false));

        assertThat(pagos.datos).hasSize(1);
    }

    @Test void unPagoQueNoAdelantaElVencimientoNoLoExtiende() {
        // Ya está pagada hasta el 1 de noviembre: pagar septiembre no extiende nada.
        empezarEn(emprende, Instant.parse("2026-11-01T05:00:00Z"), 0);

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, comando(true)))).isEqualTo("EXTENSION_SIN_EFECTO");

        assertThat(pagos.datos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
        assertThat(guardada().venceEn()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test void unPagoQueDejaElMismoVencimientoTampocoLoExtiende() {
        empezarEn(emprende, VENCE_1_OCT, 0);

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, comando(true)))).isEqualTo("EXTENSION_SIN_EFECTO");
    }

    @Test void unPagoQueAdelantaElVencimientoUnSoloSegundoSiLoExtiende() {
        empezarEn(emprende, VENCE_1_OCT.minusSeconds(1), 0);

        service.registrar(ACTOR, cuentaId, comando(true));

        assertThat(guardada().venceEn()).isEqualTo(VENCE_1_OCT);
    }

    @Test void siOtroAdministradorMovioElVencimientoEnElMedioSeAvisaYNoQuedaNada() {
        suscripciones.negarExtensiones = true;

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, comando(true)))).isEqualTo("CAMBIO_CONCURRENTE");

        assertThat(auditoria.registros).isEmpty();
    }

    // --- el apunte repetido -----------------------------------------------------------------------------------------------------------------

    @Test void elMismoPagoRepetidoSeRechazaYNoExtiendeNiAudita() {
        service.registrar(ACTOR, cuentaId, comando(false));
        auditoria.registros.clear();
        empezarEn(emprende, AHORA.plus(Duration.ofDays(5)), 3);

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, comando(true)))).isEqualTo("PAGO_DUPLICADO");

        assertThat(pagos.datos).hasSize(1);
        assertThat(guardada().venceEn()).isEqualTo(AHORA.plus(Duration.ofDays(5)));
        assertThat(auditoria.registros).isEmpty();
    }

    /** H7: el mensaje lo lee el administrador en el portal; decía «un pago por YAPE», con el código del medio. */
    @Test void elPagoRepetidoSeExplicaConElNombreDelMedioYNoConSuCodigo() {
        service.registrar(ACTOR, cuentaId, comando(false));

        DomainException e = catchThrowableOfType(DomainException.class, () -> service.registrar(ACTOR, cuentaId, comando(false)));

        assertThat(e.getMessage()).isEqualTo("Esa cuenta ya tiene un pago por Yape con la referencia «OP-123»");
    }

    // --- la fecha de pago -------------------------------------------------------------------------------------------------------------------

    @Test void laFechaDePagoPuedeSerHoyOAnteriorPeroNoFutura() {
        for (LocalDate ok : List.of(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 12), LocalDate.of(2020, 1, 1))) {
            service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, ok, null, null, false));
        }
        assertThat(pagos.datos).hasSize(3);

        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 14), null, null, false))))
                .isEqualTo("FECHA_DE_PAGO_FUTURA");
        assertThat(pagos.datos).hasSize(3);
    }

    /** «Hoy» es el de Lima, no el de UTC: a las 22:00 del 13 en Lima ya es el 14 en UTC, y un pago del 14 sigue siendo futuro. */
    @Test void hoyEsElDiaDeLimaNoElDeUtc() {
        Clock noche = Clock.fixed(Instant.parse("2026-09-14T03:00:00Z"), ZoneOffset.UTC);
        PagosDeCuentaService s = new PagosDeCuentaService(pagos, suscripciones, auditoria, uow, noche);

        assertThat(codigo(() -> s.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 14), null, null, false))))
                .isEqualTo("FECHA_DE_PAGO_FUTURA");
        s.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 13), null, null, false));
        assertThat(pagos.datos).hasSize(1);
    }

    // --- datos inválidos y cuenta inexistente -----------------------------------------------------------------------------------------------

    @Test void losDatosInvalidosSeRechazanSinGuardarNiAuditarNiExtender() {
        LocalDate hoy = LocalDate.of(2026, 9, 12);
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(HASTA, DESDE, BigDecimal.TEN, MedioDePago.YAPE, hoy, null, null, true)))).isEqualTo("PERIODO_INVALIDO");
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.ZERO, MedioDePago.YAPE, hoy, null, null, true)))).isEqualTo("MONTO_INVALIDO");
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, null, hoy, null, null, true)))).isEqualTo("MEDIO_INVALIDO");
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, null, null, null, true)))).isEqualTo("FECHA_DE_PAGO_INVALIDA");
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, hoy, "x".repeat(101), null, true)))).isEqualTo("REFERENCIA_INVALIDA");
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.YAPE, hoy, null, "x".repeat(201), true)))).isEqualTo("NOTA_INVALIDA");

        assertThat(pagos.datos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
        assertThat(guardada()).isEqualTo(activa);
    }

    @Test void unaCuentaQueNoExisteEsNoEncontrada() {
        assertThat(codigo(() -> service.registrar(ACTOR, UUID.randomUUID(), comando(false)))).isEqualTo("NO_ENCONTRADO");
        assertThat(pagos.datos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void sinComandoNoHayPago() {
        assertThat(codigo(() -> service.registrar(ACTOR, cuentaId, null))).isEqualTo("PAGO_INVALIDO");
    }

    // --- la bitácora ------------------------------------------------------------------------------------------------------------------------

    @Test void registrarDejaUnRegistroDeBitacoraConLaCuentaYElDetalleDentroDeLaTransaccion() {
        Pago p = service.registrar(ACTOR, cuentaId, comando(true));

        RegistroAuditoria r = unicoRegistro();
        assertThat(r.accion()).isEqualTo(AccionAdmin.REGISTRAR_PAGO);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isNull();
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.ocurridoEn()).isEqualTo(AHORA);
        assertThat(r.detalle()).isEqualTo("pago=" + p.id() + " periodo=2026-09-01/2026-09-30 monto=29.00 medio=YAPE fecha=2026-09-12 vence=2026-10-01");
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
    }

    @Test void sinExtenderLaBitacoraDiceQueElVencimientoNoCambio() {
        Pago p = service.registrar(ACTOR, cuentaId, comando(false));

        assertThat(unicoRegistro().detalle()).isEqualTo("pago=" + p.id() + " periodo=2026-09-01/2026-09-30 monto=29.00 medio=YAPE fecha=2026-09-12 vence=sin_cambio");
    }

    @Test void laBitacoraNoLlevaLaReferenciaNiLaNotaQueEscribioElAdministrador() {
        service.registrar(ACTOR, cuentaId, comando(false));

        assertThat(unicoRegistro().detalle()).doesNotContain("OP-123").doesNotContain("Pagó por Yape");
    }

    @Test void siLaBitacoraFallaElErrorSubeParaQueLaTransaccionSeReviertaYNoSeDaPorHecho() {
        auditoria.falla = new IllegalStateException("la tabla de auditoría no responde");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.registrar(ACTOR, cuentaId, comando(true))).isInstanceOf(IllegalStateException.class);
    }

    // --- el historial -----------------------------------------------------------------------------------------------------------------------

    @Test void elHistorialDiceLosPagosYElTotal() {
        service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 12), null, null, false));
        service.registrar(ACTOR, cuentaId, new Comando(DESDE, HASTA, BigDecimal.TEN, MedioDePago.EFECTIVO, LocalDate.of(2026, 9, 11), null, null, false));

        Pagina p = service.deLaCuenta(cuentaId, 2, 5);

        assertThat(p.pagos()).hasSize(2);
        assertThat(p.total()).isEqualTo(2);
        assertThat(pagos.consultas).containsExactly("2/5");
    }

    @Test void elHistorialDeUnaCuentaQueNoExisteEsNoEncontrado() {
        assertThat(codigo(() -> service.deLaCuenta(UUID.randomUUID(), 1, 10))).isEqualTo("NO_ENCONTRADO");
        assertThat(pagos.consultas).isEmpty();
    }

    @Test void consultarElHistorialNoEscribeNadaNiAudita() {
        service.deLaCuenta(cuentaId, 1, 10);

        assertThat(auditoria.registros).isEmpty();
        assertThat(pagos.datos).isEmpty();
    }
}
