package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.CuentaAdminResponse;
import pe.factura.adapters.rest.dto.CuentaDetalleResponse;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.util.List;
import java.util.UUID;

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
    private final DetalleCuentaAdminUseCase detalle;

    @GetMapping("/cuentas")
    @Operation(summary = "Listar las cuentas de clientes", description = """
            Todas las cuentas, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
            `X-Total-Count` y refleja la búsqueda. `q` busca sin distinguir mayúsculas en el correo y el nombre de la cuenta,
            y en la razón social de sus empresas (por fragmento) y su RUC (por prefijo). En el nombre y la razón social tampoco
            distingue tildes, diéresis ni acentos, en los dos sentidos: `libreria` encuentra «Librería» y `librería` encuentra
            «Libreria». La `ñ` **sí** se distingue de la `n`: es otra letra, y `pena` no encuentra «Peña». `ultimo_acceso` mide la actividad en el
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

    @GetMapping("/cuentas/{id}")
    @Operation(summary = "Abrir una cuenta", description = """
            Los usuarios de la cuenta (rol, si verificaron su correo y su último acceso), sus empresas (RUC, razón social, entorno y si
            tienen certificado y credenciales SOL cargados, **sin exponer su contenido**) y su actividad reciente: los 10 comprobantes
            de fecha de emisión más reciente entre todas sus empresas y las últimas 10 acciones del administrador sobre la cuenta.
            Solo lectura; no se audita. `404 NO_ENCONTRADO` si la cuenta no existe.""")
    public ApiResponse<CuentaDetalleResponse> abrir(@Parameter(description = "Id de la cuenta") @PathVariable UUID id) {
        return ApiResponse.ok(CuentaDetalleResponse.de(detalle.detalle(id)));
    }
}
