package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.CuentaAdminResponse;
import pe.factura.adapters.rest.dto.CuentaDetalleResponse;
import pe.factura.adapters.rest.dto.EstadoCuentaResponse;
import pe.factura.adapters.rest.dto.SuspenderCuentaRequest;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.in.SuspenderCuentaUseCase;
import pe.factura.application.port.in.VisibilidadDeBajas;

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
    private final SuspenderCuentaUseCase suspension;

    @GetMapping("/cuentas")
    @Operation(summary = "Listar las cuentas de clientes", description = """
            Todas las cuentas, de la más reciente a la más antigua, paginadas. El total de resultados va en la cabecera
            `X-Total-Count` y refleja la búsqueda. `q` busca sin distinguir mayúsculas en el correo y el nombre de la cuenta,
            y en la razón social de sus empresas (por fragmento) y su RUC (por prefijo). En el nombre y la razón social tampoco
            distingue tildes, diéresis ni acentos, en los dos sentidos: `libreria` encuentra «Librería» y `librería` encuentra
            «Libreria». La `ñ` **sí** se distingue de la `n`: es otra letra, y `pena` no encuentra «Peña». `ultimo_acceso` mide la actividad en el
            portal, no el uso por API key. **Las cuentas dadas de baja (#201) no salen** salvo que se pida con `bajas`: `INCLUIDAS` las mezcla y
            `SOLO` devuelve únicamente esas; el total las trata igual. El plan de la cuenta se agregará más adelante.""")
    public ResponseEntity<ApiResponse<List<CuentaAdminResponse>>> listar(
            @Parameter(description = "Búsqueda libre: correo, nombre, razón social o RUC", example = "ana@") @RequestParam(required = false) String q,
            @Parameter(description = "Qué hacer con las cuentas dadas de baja: `OCULTAS` (por defecto), `INCLUIDAS` o `SOLO`") @RequestParam(required = false) VisibilidadDeBajas bajas,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var filtro = new Filtro(q, bajas);
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

    @PostMapping("/cuentas/{id}/suspender")
    @Operation(summary = "Suspender una cuenta", description = """
            Corta el acceso al portal de todos los usuarios de la cuenta y la emisión por API de todas sus empresas: cualquier petición suya
            responde `403 CUENTA_SUSPENDIDA`, incluso con una sesión que ya estaba abierta. **No borra nada**: reactivar la cuenta lo devuelve
            todo a como estaba. Los documentos que ya se emitieron siguen enviándose a SUNAT (cortarlos los dejaría fuera de plazo). Queda en
            la bitácora de auditoría, con el motivo si se dio, en la misma transacción. `409 CUENTA_YA_SUSPENDIDA` si ya lo estaba;
            `404 NO_ENCONTRADO` si no existe; `422 MOTIVO_INVALIDO` si el motivo pasa de 200 caracteres.""")
    public ApiResponse<EstadoCuentaResponse> suspender(@Parameter(description = "Id de la cuenta") @PathVariable UUID id,
                                                       @RequestBody(required = false) SuspenderCuentaRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(EstadoCuentaResponse.de(suspension.suspender(actor, id, body == null ? null : body.motivo())));
    }

    @PostMapping("/cuentas/{id}/reactivar")
    @Operation(summary = "Reactivar una cuenta suspendida", description = """
            Devuelve la cuenta, sus usuarios y todas sus empresas a como estaban antes de suspenderla: las sesiones vuelven a servir y las
            API keys a emitir. Queda en la bitácora de auditoría. `409 CUENTA_NO_SUSPENDIDA` si ya estaba activa; `404 NO_ENCONTRADO` si no
            existe.""")
    public ApiResponse<EstadoCuentaResponse> reactivar(@Parameter(description = "Id de la cuenta") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(EstadoCuentaResponse.de(suspension.reactivar(actor, id)));
    }
}
