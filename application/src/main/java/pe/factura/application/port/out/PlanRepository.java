package pe.factura.application.port.out;

import pe.factura.domain.plan.Plan;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Los planes que se venden (#189) y su gestión desde el backoffice (#190). Las escrituras van dentro de la transacción del servicio, junto a la bitácora. */
public interface PlanRepository {
    Optional<Plan> buscar(UUID id);

    /** Igual que {@link #buscar} pero bloquea la fila hasta el final de la transacción: dos ediciones o una edición y un borrado a la vez se serializan. */
    Optional<Plan> buscarParaEditar(UUID id);

    /** Todos, activos o no, del más barato al más caro (a igual precio, por nombre). Con los límites tal como están guardados: el cambio programado sigue pendiente. */
    List<Plan> listar();

    /** El plan con el que nace toda cuenta. Siempre hay uno: la base lo garantiza. */
    Plan porDefecto();

    /**
     * Inserta o actualiza el plan, con sus límites vigentes y su cambio programado (si no tiene, se borra el que hubiera). {@code NOMBRE_DUPLICADO} si otro plan
     * ya usa ese nombre (sin importar mayúsculas): la unicidad la decide la base, así que dos altas a la vez no pasan las dos.
     */
    void guardar(Plan plan);

    /** Borra el plan si nadie lo ha usado nunca ni es el de las cuentas nuevas. {@code false} si no se pudo: no se borra nada. */
    boolean eliminar(UUID id);

    /** Cuántas cuentas tienen hoy cada plan como su suscripción vigente. Los planes que nadie usa no figuran. */
    Map<UUID, Long> cuentasPorPlan();

    /**
     * Cuántas suscripciones —vigentes o pasadas— se hicieron alguna vez a este plan, más las cuentas que esperan pasar a él (un cambio programado, #191). Si hay
     * alguna, el plan no se puede borrar (solo desactivar).
     */
    long suscripcionesDelPlan(UUID id);
}
