package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarPlanesPublicadosUseCase;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.domain.plan.Plan;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Los planes de la página de precios (H20). El repositorio ya los da del más barato al más caro. */
@RequiredArgsConstructor
public class ConsultarPlanesPublicadosService implements ConsultarPlanesPublicadosUseCase {
    private final PlanRepository planes;
    private final Clock clock;

    @Override public List<Plan> publicados() {
        Instant ahora = clock.instant();
        return planes.listar().stream().filter(p -> p.activo() && p.visibleEnPublicidad()).map(p -> p.vigenteEn(ahora)).toList();
    }
}
