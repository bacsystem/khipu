package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.AdministradorResponse;
import pe.factura.adapters.rest.dto.CrearAdministradorRequest;
import pe.factura.application.port.in.CrearAdministradorUseCase;

/**
 * Alta de administradores de la plataforma. Sin registro público a propósito (#175): la crea quien ya
 * tiene X-Platform-Key (bootstrap) o un administrador ya autenticado (ver {@link AdminAuthFilter}).
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdministradorController {
    private final CrearAdministradorUseCase crear;

    @PostMapping("/administradores")
    @Operation(summary = "Crear una cuenta de administrador", description = "Alta de un administrador de la plataforma (email + password); no pertenece a ninguna cuenta de cliente.")
    public ResponseEntity<ApiResponse<AdministradorResponse>> crear(@Valid @RequestBody CrearAdministradorRequest body) {
        var a = crear.crear(body.email(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(AdministradorResponse.de(a)));
    }
}
