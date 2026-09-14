package pe.factura.adapters.rest;

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
@RequiredArgsConstructor
public class AdminTenantController {
    private final AdministrarTenantUseCase admin;

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<CrearTenantResponse>> crear(@Valid @RequestBody CrearTenantRequest body) {
        var r = admin.crearTenant(body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new CrearTenantResponse(r.tenant().id(), r.tenant().ruc(), r.apiKeyEnClaro())));
    }
}
