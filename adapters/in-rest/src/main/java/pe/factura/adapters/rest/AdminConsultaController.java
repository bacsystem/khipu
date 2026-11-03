package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.PadronRucResponse;
import pe.factura.application.port.in.ConsultarPadronRucUseCase;

/**
 * El padrón de RUC desde el backoffice (#575): el alta asistida de un cliente escribe un RUC y todavía no hay empresa a la que contarle el cupo. Autenticado por
 * {@link AdminAuthFilter}; el cupo es de cada administrador, y la clave de plataforma comparte uno.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminConsultaController {
    private final ConsultarPadronRucUseCase padron;

    @GetMapping("/consultas/ruc/{ruc}")
    @Operation(operationId = "consultarRucAdmin", summary = "Consultar un RUC en el padrón de SUNAT (backoffice)", description = """
            Lo mismo que `GET /v1/consultas/ruc/{ruc}`, para el backoffice: razón social, estado, condición y domicilio fiscal del padrón reducido. Mismos errores
            (`400 PARAMETRO_INVALIDO`, `404 NO_ENCONTRADO`, `503 PADRON_NO_CARGADO`, `429 DEMASIADAS_CONSULTAS`) y el mismo cupo, contado por administrador.""")
    public ApiResponse<PadronRucResponse> ruc(HttpServletRequest req, @Parameter(example = "20100066603") @PathVariable String ruc) {
        return ApiResponse.ok(PadronRucResponse.de(padron.consultar(ConsultarPadronRucUseCase.Solicitante.backoffice(AdministradorActual.actor(req)), ruc)));
    }
}
