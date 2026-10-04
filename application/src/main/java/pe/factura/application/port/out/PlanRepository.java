package pe.factura.application.port.out;

import pe.factura.domain.plan.Plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los planes que se venden (#189). Por ahora solo lectura: crearlos y editarlos llega con el CRUD del backoffice (#190). */
public interface PlanRepository {
    Optional<Plan> buscar(UUID id);

    /** Todos, activos o no, del más barato al más caro (a igual precio, por nombre). */
    List<Plan> listar();

    /** El plan con el que nace toda cuenta. Siempre hay uno: la base lo garantiza. */
    Plan porDefecto();
}
