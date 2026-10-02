package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.CuentaAdminResponse;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.util.List;

/**
 * Listado de cuentas de clientes del backoffice (#180). Solo lectura. Autenticado por {@link AdminAuthFilter}: la clave de
 * plataforma o el JWT de un administrador; un JWT de cliente o una API key de tenant no pasan.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminCuentaController {
    private final ListarCuentasAdminUseCase cuentas;

    @GetMapping("/cuentas")
    @Operation(summary = "Listar las cuentas de clientes", description = """
            Todas las cuentas, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
            `X-Total-Count` y refleja la búsqueda. `q` busca sin distinguir mayúsculas en el correo y el nombre de la cuenta,
            y en la razón social de sus empresas (por fragmento) y su RUC (por prefijo). `ultimo_acceso` mide la actividad en el
            portal, no el uso por API key. El estado y el plan de la cuenta se agregarán más adelante.""")
    public ResponseEntity<ApiResponse<List<CuentaAdminResponse>>> listar(
            @Parameter(description = "Búsqueda libre: correo, nombre, razón social o RUC", example = "ana@") @RequestParam(required = false) String q,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var filtro = new Filtro(q);
        List<CuentaAdminResponse> datos = cuentas.listar(filtro, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)))
                .stream().map(CuentaAdminResponse::de).toList();
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(cuentas.contar(filtro))).body(ApiResponse.ok(datos));
    }
}
