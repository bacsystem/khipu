package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.CrearEmpresaRequest;
import pe.factura.adapters.rest.dto.EmpresaVistaResponse;
import pe.factura.application.port.in.GestionarEmpresasUseCase;

import java.util.List;

/** Empresas (tenants) de la cuenta autenticada por JWT en el portal. */
@RestController
@RequestMapping("/v1/empresas")
@RequiredArgsConstructor
public class EmpresasController {
    private final GestionarEmpresasUseCase empresas;

    @GetMapping
    public ApiResponse<List<EmpresaVistaResponse>> listar(HttpServletRequest req) {
        return ApiResponse.ok(empresas.listar(CuentaActual.id(req)).stream().map(EmpresaVistaResponse::de).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmpresaVistaResponse>> crear(HttpServletRequest req, @Valid @RequestBody CrearEmpresaRequest body) {
        var t = empresas.crear(CuentaActual.id(req), body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(EmpresaVistaResponse.de(t)));
    }
}
