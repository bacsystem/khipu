package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.FichaDeComprobanteResponse;
import pe.factura.application.port.in.ConsultarComprobanteAdminUseCase;

import java.util.UUID;

/** La ficha de un comprobante en el backoffice (#251). Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador. */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma")
@RequiredArgsConstructor
public class AdminComprobanteController {
    private final ConsultarComprobanteAdminUseCase consulta;

    @GetMapping("/comprobantes/{id}")
    @Operation(summary = "La ficha de un comprobante", description = """
            Un comprobante de cualquier empresa: su empresa (y la cuenta, si tiene), su identidad ante SUNAT, fecha de emisión, estado, intentos de envío, el último error,
            lo que respondió SUNAT y si el XML y el CDR están guardados (sin su contenido). Solo lectura; no se audita. `404 NO_ENCONTRADO` si no existe,
            `400 PARAMETRO_INVALIDO` si el id no es un UUID.""")
    public ApiResponse<FichaDeComprobanteResponse> ficha(@Parameter(description = "Id del comprobante") @PathVariable UUID id) {
        return ApiResponse.ok(FichaDeComprobanteResponse.de(consulta.ficha(id)));
    }
}
