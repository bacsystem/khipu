package pe.factura.application.port.in;

import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Crear, editar, listar, desactivar y borrar planes desde el backoffice (#190), para cambiar precios o límites sin un despliegue. Todo cambio queda en la bitácora a
 * nombre de {@code actor}, en la misma transacción. Cambiar los límites de un plan afecta al ciclo siguiente, no al que está en curso: nunca regala documentos del mes
 * corriente ni le corta a nadie.
 */
public interface GestionarPlanesUseCase {
    /** Un plan tal como manda hoy, con cuántas cuentas lo tienen vigente. {@code plan.programado()} es el cambio de límites que espera al ciclo siguiente. */
    record PlanConUso(Plan plan, long cuentas) {}

    /**
     * Lo que se pide para crear o editar un plan. {@code visibleEnPublicidad} (H20): si sale en la página de precios; nulo es «como estaba» al editar y «sí» al crear.
     */
    record DatosDePlan(String nombre, BigDecimal precioMensual, Limites limites, Boolean visibleEnPublicidad) {
        public DatosDePlan(String nombre, BigDecimal precioMensual, Limites limites) { this(nombre, precioMensual, limites, null); }
    }

    /** Todos los planes, activos o no, del más barato al más caro. */
    List<PlanConUso> listar();

    /** Nace activo. {@code NOMBRE_DUPLICADO} si ya hay uno con ese nombre; los datos inválidos se rechazan con el código del dato (precio, límite, nombre…). */
    PlanConUso crear(ActorAdmin actor, DatosDePlan datos);

    /** Nombre y precio al instante; los límites, desde el ciclo siguiente. {@code NO_ENCONTRADO} si no existe; sin ningún cambio no escribe ni deja registro. */
    PlanConUso editar(ActorAdmin actor, UUID id, DatosDePlan datos);

    /** Lo saca de la oferta sin tocar a las cuentas que ya lo tienen. {@code PLAN_POR_DEFECTO} si es el de las cuentas nuevas; {@code PLAN_YA_INACTIVO} si ya lo estaba. */
    PlanConUso desactivar(ActorAdmin actor, UUID id);

    /** {@code PLAN_YA_ACTIVO} si ya lo estaba. */
    PlanConUso activar(ActorAdmin actor, UUID id);

    /**
     * Solo un plan que nadie usó nunca. {@code PLAN_EN_USO} si alguna cuenta lo tiene o lo tuvo (hay que desactivarlo); {@code PLAN_POR_DEFECTO} si es el de las cuentas
     * nuevas; {@code NO_ENCONTRADO} si no existe.
     */
    void eliminar(ActorAdmin actor, UUID id);
}
