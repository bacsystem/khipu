package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.application.port.out.SuscripcionRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.plan.DireccionDeCambio;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * El plan de una cuenta (#191). Subir (o renovar) entra ya: se cierra la suscripción y se abre la nueva en una sola sentencia, condicionada a que la activa siga siendo la
 * que el administrador vio. Bajar queda **programado** para el inicio del ciclo siguiente y no toca nada de hoy; un trabajo ({@link #aplicarVencidos}) lo convierte en la
 * suscripción activa cuando llega la fecha. El cambio y su registro de bitácora van en la misma transacción.
 */
@RequiredArgsConstructor
public class CambiarPlanDeCuentaService implements CambiarPlanDeCuentaUseCase, AplicarCambiosDePlanUseCase {
    /** Cuántas cuentas se aplican como mucho por pasada: acota el trabajo; lo que quede se hace en la siguiente. */
    private static final int POR_PASADA = 200;

    private final PlanRepository planes;
    private final SuscripcionRepository suscripciones;
    private final ConsultarConsumoUseCase consumos;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public PlanDeCuenta plan(UUID cuentaId) { return vista(cargar(cuentaId), clock.instant()); }

    @Override public Previsualizacion previsualizar(UUID cuentaId, UUID planId) {
        PlanesDeCuenta p = cargar(cuentaId);
        Plan nuevo = planAsignable(planId);
        Instant ahora = clock.instant();
        // Si ya llegó la fecha de una bajada que todavía no se aplicó, el plan que manda es el nuevo: desde ahí se mide si lo que se pide sube o baja.
        Plan actual = planDe(p.planVigenteEn(ahora)).vigenteEn(ahora);
        DireccionDeCambio direccion = DireccionDeCambio.entre(actual, nuevo);
        ConsultarConsumoUseCase.ConsumoDeCuenta consumo = consumos.deCuenta(cuentaId, null);
        Limite limite = nuevo.vigenteEn(ahora).limites().documentosAlMes();
        Efecto efecto = direccion.esInmediata() ? Efecto.INMEDIATO : Efecto.CICLO_SIGUIENTE;
        return new Previsualizacion(cuentaId, actual, nuevo, direccion, efecto, direccion.esInmediata() ? ahora : CicloMensual.inicioDelSiguiente(ahora),
                consumo.mes(), consumo.documentos(), limite, !limite.ilimitado() && consumo.documentos() > limite.maximo());
    }

    @Override public PlanDeCuenta cambiar(ActorAdmin actor, UUID cuentaId, UUID planId, Instant venceEn, Integer diasDeGracia) {
        if (cuentaId == null) throw new DomainException("NO_ENCONTRADO", "La cuenta no existe");
        int gracia = diasDeGracia == null ? 0 : diasDeGracia;
        if (gracia < 0 || gracia > GRACIA_MAX_DIAS) throw new DomainException("GRACIA_INVALIDA", "Los días de gracia van de 0 a " + GRACIA_MAX_DIAS + ": " + gracia);
        Plan nuevo = planAsignable(planId);
        if (nuevo.precioMensual().signum() > 0 && venceEn == null)
            throw new DomainException("VENCIMIENTO_REQUERIDO", "Un plan de pago necesita una fecha de vencimiento: hasta cuándo está pagado");
        Instant ahora = clock.instant();
        return uow.ejecutar(() -> {
            PlanesDeCuenta p = aplicandoLoVencido(cargar(cuentaId), ahora);
            Plan actual = planDe(p.activa().planId());
            DireccionDeCambio direccion = DireccionDeCambio.entre(actual, nuevo);
            Instant aplicaDesde;
            if (direccion.esInmediata()) {
                aplicaDesde = ahora;
                PlanesDeCuenta despues = p.cambiarA(UUID.randomUUID(), nuevo.id(), ahora, venceEn, gracia);
                if (!suscripciones.cambiar(p.activa(), despues.activa()))
                    throw new DomainException("CAMBIO_CONCURRENTE", "Otro administrador cambió el plan de la cuenta mientras tanto: vuelve a mirarlo");
            } else {
                aplicaDesde = CicloMensual.inicioDelSiguiente(ahora);
                suscripciones.programar(cuentaId, new CambioDePlan(nuevo.id(), aplicaDesde, venceEn, gracia));
            }
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.CAMBIAR_PLAN, cuentaId, null,
                    DetalleDeCambioDePlan.de(actual, nuevo, direccion, direccion.esInmediata() ? null : aplicaDesde, venceEn, gracia), ahora));
            return vista(suscripciones.deLaCuenta(cuentaId).orElseThrow(), ahora);
        });
    }

    @Override public Resultado aplicarVencidos() {
        Instant ahora = clock.instant();
        int aplicados = 0;
        int fallidos = 0;
        for (UUID cuentaId : suscripciones.cuentasConCambioVencido(ahora, POR_PASADA)) {
            try {
                if (uow.ejecutar(() -> aplicar(cuentaId, ahora))) aplicados++;
            } catch (RuntimeException e) {
                fallidos++;
            }
        }
        return new Resultado(aplicados, fallidos);
    }

    /** Sin bitácora: aplicar lo que ya se había decidido no es una acción de nadie, y el registro de cuando se programó ya dice quién. */
    private boolean aplicar(UUID cuentaId, Instant ahora) {
        PlanesDeCuenta p = suscripciones.deLaCuenta(cuentaId).orElse(null);
        if (p == null || p.programado() == null || ahora.isBefore(p.programado().aplicaDesde())) return false;
        PlanesDeCuenta aplicada = p.aplicarProgramadoEn(ahora, UUID.randomUUID());
        return suscripciones.cambiar(p.activa(), aplicada.activa());
    }

    private PlanesDeCuenta aplicandoLoVencido(PlanesDeCuenta p, Instant ahora) {
        if (p.programado() == null || ahora.isBefore(p.programado().aplicaDesde())) return p;
        PlanesDeCuenta aplicada = p.aplicarProgramadoEn(ahora, UUID.randomUUID());
        if (!suscripciones.cambiar(p.activa(), aplicada.activa()))
            throw new DomainException("CAMBIO_CONCURRENTE", "Otro administrador cambió el plan de la cuenta mientras tanto: vuelve a mirarlo");
        return aplicada;
    }

    /** Toda cuenta tiene siempre una suscripción (la base lo garantiza), así que «no tiene» y «no existe» son lo mismo. */
    private PlanesDeCuenta cargar(UUID cuentaId) {
        if (cuentaId == null) throw new DomainException("NO_ENCONTRADO", "La cuenta no existe");
        return suscripciones.deLaCuenta(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La cuenta no existe"));
    }

    private Plan planDe(UUID id) { return planes.buscar(id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "El plan no existe")); }

    private Plan planAsignable(UUID planId) {
        if (planId == null) throw new DomainException("NO_ENCONTRADO", "El plan no existe");
        Plan p = planDe(planId);
        if (!p.activo()) throw new DomainException("PLAN_INACTIVO", "El plan «" + p.nombre() + "» está fuera de la oferta: no se puede asignar");
        return p;
    }

    private PlanDeCuenta vista(PlanesDeCuenta p, Instant ahora) {
        Suscripcion activa = p.activa();
        Programado programado = p.programado() == null ? null : new Programado(planDe(p.programado().planId()).vigenteEn(ahora), p.programado());
        return new PlanDeCuenta(p.cuentaId(), planDe(activa.planId()).vigenteEn(ahora), activa, activa.estadoEn(ahora), activa.hastaCuandoCubre(), programado);
    }

    /** Cómo se cuenta un cambio de plan en la bitácora: una línea corta con quién queda a cargo el plan, desde cuál, cuándo entra y con qué vencimiento. */
    static final class DetalleDeCambioDePlan {
        private DetalleDeCambioDePlan() {}

        static String de(Plan desde, Plan hacia, DireccionDeCambio direccion, Instant aplicaDesde, Instant venceEn, int gracia) {
            return "desde=" + desde.nombre() + " hacia=" + hacia.nombre() + " direccion=" + direccion + " efecto=" + (direccion.esInmediata() ? Efecto.INMEDIATO : Efecto.CICLO_SIGUIENTE)
                    + (aplicaDesde == null ? "" : " aplica_desde=" + fecha(aplicaDesde)) + " vence=" + (venceEn == null ? "sin_vencimiento" : fecha(venceEn)) + " gracia=" + gracia;
        }

        private static String fecha(Instant i) { return i.atZone(CicloMensual.ZONA).toLocalDate().toString(); }
    }
}
