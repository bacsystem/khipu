package pe.factura.application.port.in;

import pe.factura.domain.plan.Plan;

import java.util.List;

/**
 * Los planes que se publican en la página de precios (H20): los activos marcados como visibles, con los límites que mandan hoy, del más barato al más caro.
 * Un plan a medida para un cliente (activo pero no visible) no sale. Lectura pública: no expone cuántas cuentas tiene cada plan ni los cambios programados.
 */
public interface ConsultarPlanesPublicadosUseCase {
    List<Plan> publicados();
}
