package pe.factura.adapters.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.CrearTenantRequest;
import pe.factura.application.port.in.AdministrarTenantUseCase;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
public class AdminTenantController {
    private final AdministrarTenantUseCase admin;
    public AdminTenantController(AdministrarTenantUseCase admin) { this.admin = admin; }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(@Valid @RequestBody CrearTenantRequest body) {
        var r = admin.crearTenant(body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "tenant_id", r.tenant().id(), "ruc", r.tenant().ruc(), "api_key", r.apiKeyEnClaro())));
    }
}
