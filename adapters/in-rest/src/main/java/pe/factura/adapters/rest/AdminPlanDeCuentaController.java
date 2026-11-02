package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.CambiarPlanRequest;
import pe.factura.adapters.rest.dto.PlanDeCuentaResponse;
import pe.factura.adapters.rest.dto.PrevisualizacionDePlanResponse;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.domain.DomainException;

import java.util.UUID;

/**
 * El plan de una cuenta desde el backoffice (#191): verlo, previsualizar un cambio y hacerlo. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de
 * un administrador. Subir de plan entra ya; bajar, al ciclo siguiente.
 */
@RestController
@RequestMapping("/v1/admin/cuentas/{id}/plan")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminPlanDeCuentaController {
    private final CambiarPlanDeCuentaUseCase planes;

    @GetMapping
    @Operation(summary = "El plan de una cuenta", description = """
            Cuál es el plan de la cuenta hoy, en qué estado de pago está (`VIGENTE`, `EN_GRACIA` si venció pero sigue en sus días de gracia, `VENCIDA`), hasta cuándo
            se la sirve (gracia incluida) y, si hay, la bajada de plan que espera el inicio del ciclo siguiente. `404 NO_ENCONTRADO` si la cuenta no existe.""")
    public ApiResponse<PlanDeCuentaResponse> ver(@Parameter(description = "Id de la cuenta") @PathVariable UUID id) {
        return ApiResponse.ok(PlanDeCuentaResponse.de(planes.plan(id)));
    }

    @GetMapping("/previsualizacion")
    @Operation(summary = "Previsualizar un cambio de plan", description = """
            Lo que pasaría con un cambio, **sin hacerlo**: si sube, baja o renueva (lo decide el precio), cuándo entra (subir y renovar, ya; bajar, al inicio del ciclo
            siguiente), cuántos documentos consumió la cuenta este mes —solo los comprobantes aceptados por SUNAT— y si ese consumo ya supera el tope de documentos del plan
            nuevo. `404 NO_ENCONTRADO` (cuenta o plan); `409 PLAN_INACTIVO` si el plan está fuera de la oferta.""")
    public ApiResponse<PrevisualizacionDePlanResponse> previsualizar(@Parameter(description = "Id de la cuenta") @PathVariable UUID id,
                                                                     @Parameter(description = "Id del plan al que se pasaría") @RequestParam("plan_id") UUID planId) {
        return ApiResponse.ok(PrevisualizacionDePlanResponse.de(planes.previsualizar(id, planId)));
    }

    @PostMapping
    @Operation(summary = "Cambiar el plan de una cuenta", description = """
            **Subir de plan tiene efecto inmediato; bajar, al ciclo siguiente** (el mes calendario en America/Lima): una bajada queda programada y la cuenta sigue con su
            plan de hoy hasta entonces. Lo que es subir o bajar lo decide el precio; el mismo plan con otro vencimiento es una renovación y entra ya. Un cambio inmediato
            cancela la bajada que esperaba. `vence_en` es obligatorio para un plan de pago (`422 VENCIMIENTO_REQUERIDO`) y debe ser posterior al inicio; `dias_de_gracia`
            va de 0 a 90 (`422 GRACIA_INVALIDA`). `409 PLAN_INACTIVO` si el plan está fuera de la oferta; `409 CAMBIO_CONCURRENTE` si otro administrador cambió el plan
            en el medio (hay que mirarlo de nuevo); `404 NO_ENCONTRADO`. Queda en la bitácora, con quién, de qué plan a cuál y cuándo entra.""")
    public ApiResponse<PlanDeCuentaResponse> cambiar(@Parameter(description = "Id de la cuenta") @PathVariable UUID id, @RequestBody CambiarPlanRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        if (body.planId() == null) throw new DomainException("PLAN_REQUERIDO", "Falta el plan al que pasa la cuenta");
        return ApiResponse.ok(PlanDeCuentaResponse.de(planes.cambiar(actor, id, body.planId(), body.venceEn(), body.diasDeGracia())));
    }
}
