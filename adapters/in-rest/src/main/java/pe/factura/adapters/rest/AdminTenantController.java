package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.CrearTenantRequest;
import pe.factura.adapters.rest.dto.CrearTenantResponse;
import pe.factura.application.port.in.AdministrarTenantUseCase;

@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key`. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminTenantController {
    private final AdministrarTenantUseCase admin;

    @PostMapping("/tenants")
    @Operation(summary = "Crear un tenant (empresa) con su primera API key", description = "Alta administrativa de una empresa sin pasar por el portal; devuelve la API key inicial, que se muestra una sola vez.")
    public ResponseEntity<ApiResponse<CrearTenantResponse>> crear(@Valid @RequestBody CrearTenantRequest body) {
        var r = admin.crearTenant(body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new CrearTenantResponse(r.tenant().id(), r.tenant().ruc(), r.apiKeyEnClaro())));
    }
}
