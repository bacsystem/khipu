package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.GestionarPlanesUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDeLimites;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestión de planes (#190). Cada cambio y su registro de bitácora van en una sola transacción, y el plan se lee con bloqueo: dos administradores editando (o uno
 * editando y otro borrando) el mismo plan se serializan en vez de pisarse. Los límites nuevos no se tocan aquí: {@link Plan#editar} los deja programados para el
 * ciclo siguiente.
 */
@RequiredArgsConstructor
public class GestionarPlanesService implements GestionarPlanesUseCase {
    private final PlanRepository planes;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public List<PlanConUso> listar() {
        Instant ahora = clock.instant();
        Map<UUID, Long> usos = planes.cuentasPorPlan();
        return planes.listar().stream().map(p -> p.vigenteEn(ahora)).map(p -> new PlanConUso(p, usos.getOrDefault(p.id(), 0L))).toList();
    }

    @Override public PlanConUso crear(ActorAdmin actor, DatosDePlan datos) {
        exigirDatos(datos);
        Instant ahora = clock.instant();
        Plan nuevo = new Plan(UUID.randomUUID(), datos.nombre(), datos.precioMensual(), datos.limites(), EstadoPlan.ACTIVO, false);
        uow.ejecutar(() -> {
            planes.guardar(nuevo);
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.CREAR_PLAN, null, null, DetalleDePlan.alta(nuevo), ahora));
        });
        return new PlanConUso(nuevo, 0);
    }

    @Override public PlanConUso editar(ActorAdmin actor, UUID id, DatosDePlan datos) {
        exigirDatos(datos);
        Instant ahora = clock.instant();
        return uow.ejecutar(() -> {
            Plan vigente = leerParaEditar(id).vigenteEn(ahora);
            Plan editado = vigente.editar(datos.nombre(), datos.precioMensual(), datos.limites(), ahora);
            if (editado.equals(vigente)) return conUso(vigente);
            planes.guardar(editado);
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.EDITAR_PLAN, null, null, DetalleDePlan.edicion(vigente, editado), ahora));
            return conUso(editado);
        });
    }

    @Override public PlanConUso desactivar(ActorAdmin actor, UUID id) {
        Instant ahora = clock.instant();
        return uow.ejecutar(() -> {
            Plan inactivo = leerParaEditar(id).desactivar();
            planes.guardar(inactivo);
            PlanConUso r = conUso(inactivo);
            // Las cuentas que ya lo tienen siguen con él: la bitácora dice cuántas, porque es lo que el administrador querría saber después.
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.DESACTIVAR_PLAN, null, null, "plan=" + inactivo.nombre() + " cuentas=" + r.cuentas(), ahora));
            return r;
        });
    }

    @Override public PlanConUso activar(ActorAdmin actor, UUID id) {
        Instant ahora = clock.instant();
        return uow.ejecutar(() -> {
            Plan activo = leerParaEditar(id).activar();
            planes.guardar(activo);
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.ACTIVAR_PLAN, null, null, "plan=" + activo.nombre(), ahora));
            return conUso(activo);
        });
    }

    @Override public void eliminar(ActorAdmin actor, UUID id) {
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            Plan plan = leerParaEditar(id);
            if (plan.porDefecto()) throw new DomainException("PLAN_POR_DEFECTO", "El plan por defecto de las cuentas nuevas no se puede borrar");
            long cuentas = planes.cuentasPorPlan().getOrDefault(id, 0L);
            if (cuentas > 0)
                throw new DomainException("PLAN_EN_USO", "El plan lo tiene " + cuentas + (cuentas == 1 ? " cuenta" : " cuentas") + ": desactívalo en vez de borrarlo");
            if (planes.suscripcionesDelPlan(id) > 0)
                throw new DomainException("PLAN_EN_USO", "El plan tiene historial de suscripciones: desactívalo en vez de borrarlo");
            // Entre mirar y borrar el borrado sigue siendo condicional: si una cuenta lo tomó justo ahora, la base no lo borra.
            if (!planes.eliminar(id)) throw new DomainException("PLAN_EN_USO", "El plan está en uso: desactívalo en vez de borrarlo");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.ELIMINAR_PLAN, null, null, "plan=" + plan.nombre(), ahora));
        });
    }

    private Plan leerParaEditar(UUID id) {
        if (id == null) throw new DomainException("NO_ENCONTRADO", "El plan no existe");
        return planes.buscarParaEditar(id).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "El plan no existe"));
    }

    private PlanConUso conUso(Plan plan) { return new PlanConUso(plan, planes.cuentasPorPlan().getOrDefault(plan.id(), 0L)); }

    private static void exigirDatos(DatosDePlan datos) {
        if (datos == null) throw new DomainException("DATOS_INVALIDOS", "Faltan los datos del plan");
    }

    /** Cómo se cuenta un plan en la bitácora: una línea corta, sin nada que no sea del plan. */
    static final class DetalleDePlan {
        private DetalleDePlan() {}

        static String alta(Plan p) {
            Limites l = p.limites();
            return "plan=" + p.nombre() + " precio=" + p.precioMensual().toPlainString() + " documentos=" + texto(l.documentosAlMes()) + " ruc=" + l.rucs()
                    + " usuarios=" + texto(l.usuarios()) + " api_keys=" + texto(l.apiKeys()) + " retencion=" + l.retencionAnios();
        }

        /** Qué cambió, de qué a qué. Los límites se comparan contra los vigentes: lo programado es relativo a ellos. */
        static String edicion(Plan antes, Plan despues) {
            List<String> partes = new ArrayList<>();
            partes.add("plan=" + antes.nombre());
            cambio(partes, "nombre", antes.nombre(), despues.nombre());
            cambio(partes, "precio", antes.precioMensual().toPlainString(), despues.precioMensual().toPlainString());
            CambioDeLimites programado = despues.programado();
            if (programado != null) {
                Limites a = antes.limites();
                Limites n = programado.limites();
                cambio(partes, "documentos", texto(a.documentosAlMes()), texto(n.documentosAlMes()));
                cambio(partes, "ruc", String.valueOf(a.rucs()), String.valueOf(n.rucs()));
                cambio(partes, "usuarios", texto(a.usuarios()), texto(n.usuarios()));
                cambio(partes, "api_keys", texto(a.apiKeys()), texto(n.apiKeys()));
                cambio(partes, "retencion", String.valueOf(a.retencionAnios()), String.valueOf(n.retencionAnios()));
                partes.add("limites_desde=" + programado.aplicaDesde().atZone(CicloMensual.ZONA).toLocalDate());
            } else if (antes.programado() != null) {
                partes.add("limites_programados=cancelados");
            }
            return String.join("; ", partes);
        }

        private static void cambio(List<String> partes, String campo, String antes, String despues) {
            if (!antes.equals(despues)) partes.add(campo + "=" + antes + ">" + despues);
        }

        private static String texto(Limite l) { return l.ilimitado() ? "ilimitado" : String.valueOf(l.maximo()); }
    }
}
