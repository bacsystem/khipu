package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.PreferenciasDeEmisionDtos.PreferenciasRequest;
import pe.factura.adapters.rest.dto.PreferenciasDeEmisionDtos.PreferenciasResponse;
import pe.factura.application.port.in.PreferenciasDeEmisionUseCase;

@RestController
@RequestMapping("/v1/empresa")
@RequiredArgsConstructor
@Tag(name = "Preferencias de emisión", description = """
        Cómo se emite desde el portal: con `opciones_avanzadas` apagada (por defecto) el modal de emisión ofrece solo lo habitual (crédito, detracción
        y correo); encendida, también el tipo de operación/exportación, anticipos, percepción, retención, más opciones y referencias. No cambia la API:
        un integrador puede enviar cualquier campo con o sin ella.""")
public class PreferenciasDeEmisionController {
    private final PreferenciasDeEmisionUseCase preferencias;

    @GetMapping("/preferencias-emision")
    @Operation(summary = "Ver las preferencias de emisión")
    public ApiResponse<PreferenciasResponse> verPreferenciasDeEmision(HttpServletRequest req) {
        return ApiResponse.ok(PreferenciasResponse.de(preferencias.obtener(TenantActual.id(req))));
    }

    @PutMapping("/preferencias-emision")
    @Operation(summary = "Guardar las preferencias de emisión", description = "Solo un administrador de la cuenta (`403 ROL_INSUFICIENTE` para los demás roles).")
    public ApiResponse<PreferenciasResponse> guardarPreferenciasDeEmision(HttpServletRequest req, @Valid @RequestBody PreferenciasRequest body) {
        return ApiResponse.ok(PreferenciasResponse.de(preferencias.guardar(TenantActual.id(req), body.aDominio())));
    }
}
