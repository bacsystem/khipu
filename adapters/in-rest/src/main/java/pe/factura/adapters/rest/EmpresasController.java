package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.CrearEmpresaRequest;
import pe.factura.application.port.in.GestionarEmpresasUseCase;
import pe.factura.domain.tenant.Tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Empresas (tenants) de la cuenta autenticada por JWT en el portal. */
@RestController
@RequestMapping("/v1/empresas")
@RequiredArgsConstructor
public class EmpresasController {
    private final GestionarEmpresasUseCase empresas;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar(HttpServletRequest req) {
        return ApiResponse.ok(empresas.listar(CuentaActual.id(req)).stream().map(EmpresasController::vista).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> crear(HttpServletRequest req, @Valid @RequestBody CrearEmpresaRequest body) {
        Tenant t = empresas.crear(CuentaActual.id(req), body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(vista(t)));
    }

    private static Map<String, Object> vista(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id());
        m.put("ruc", t.ruc());
        m.put("razon_social", t.razonSocial());
        m.put("entorno", t.entorno());
        m.put("tiene_certificado", t.certificado() != null);
        m.put("tiene_credenciales_sol", t.sol() != null);
        return m;
    }
}
