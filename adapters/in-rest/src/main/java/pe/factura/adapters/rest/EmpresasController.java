package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Cuenta y empresas", description = "Empresas (RUC) asociadas a la cuenta del portal. Solo con sesión JWT: una API key pertenece a una sola empresa y no puede listar ni crear otras.")
@RequiredArgsConstructor
public class EmpresasController {
    private final GestionarEmpresasUseCase empresas;

    @GetMapping
    @Operation(summary = "Listar las empresas de la cuenta")
    public ApiResponse<List<EmpresaVistaResponse>> listar(HttpServletRequest req) {
        return ApiResponse.ok(empresas.listar(CuentaActual.id(req)).stream().map(EmpresaVistaResponse::de).toList());
    }

    @PostMapping
    @Operation(summary = "Registrar una empresa", description = "Alta de una razón social (RUC único en la plataforma) con su entorno SUNAT. Después hay que cargar certificado, credenciales SOL y al menos una serie antes de emitir.")
    public ResponseEntity<ApiResponse<EmpresaVistaResponse>> crear(HttpServletRequest req, @Valid @RequestBody CrearEmpresaRequest body) {
        var t = empresas.crear(CuentaActual.id(req), body.ruc(), body.razonSocial(), body.entorno());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(EmpresaVistaResponse.de(t)));
    }
}
