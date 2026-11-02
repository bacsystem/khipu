package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.PagoResponse;
import pe.factura.adapters.rest.dto.RegistrarPagoRequest;
import pe.factura.application.port.in.ConsultarPagosUseCase;
import pe.factura.application.port.in.RegistrarPagoUseCase;

import java.util.List;
import java.util.UUID;

/**
 * Los pagos de una cuenta desde el backoffice (#194): registrarlos a mano y ver su historial. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de
 * un administrador. Sin pasarela de pago, a propósito.
 */
@RestController
@RequestMapping("/v1/admin/cuentas/{id}/pagos")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminPagoController {
    private final RegistrarPagoUseCase registrar;
    private final ConsultarPagosUseCase consultar;

    @GetMapping
    @Operation(summary = "El historial de pagos de una cuenta", description = """
            Los pagos que se registraron a mano para la cuenta, del más reciente al más antiguo (por fecha de pago, luego por cuándo se registraron), paginados. El total va en
            la cabecera `X-Total-Count`. Solo lectura. `404 NO_ENCONTRADO` si la cuenta no existe.""")
    public ResponseEntity<ApiResponse<List<PagoResponse>>> historial(
            @Parameter(description = "Id de la cuenta") @PathVariable UUID id,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var p = consultar.deLaCuenta(id, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)));
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.pagos().stream().map(PagoResponse::de).toList()));
    }

    @PostMapping
    @Operation(summary = "Registrar el pago de una cuenta", description = """
            Anota a mano que el cliente pagó: periodo que cubre, monto en soles, medio, fecha y, si se quiere, una referencia (número de operación) y una nota. **No hay pasarela de
            pago, a propósito.** Con `extender_vencimiento` el vencimiento de la suscripción pasa a ser la medianoche (Lima) del día siguiente a `periodo_hasta`: solo si eso lo
            adelanta (`409 EXTENSION_SIN_EFECTO`) y solo en un plan que vence (`409 PLAN_SIN_VENCIMIENTO`); no cambia el plan ni los días de gracia. Una cuenta no puede repetir
            medio y referencia (`409 PAGO_DUPLICADO`), así un reintento no anota el pago dos veces. `409 CAMBIO_CONCURRENTE` si otro administrador movió el vencimiento en el medio.
            `422` por un periodo, monto, medio o fecha inválidos (`PERIODO_INVALIDO`, `MONTO_INVALIDO`, `MEDIO_INVALIDO`, `FECHA_DE_PAGO_INVALIDA`, `FECHA_DE_PAGO_FUTURA`,
            `REFERENCIA_INVALIDA`, `NOTA_INVALIDA`). `404 NO_ENCONTRADO` si la cuenta no existe. Queda en la bitácora, en la misma transacción: periodo, monto, medio y nuevo
            vencimiento; la referencia y la nota no.""")
    public ResponseEntity<ApiResponse<PagoResponse>> registrar(@Parameter(description = "Id de la cuenta") @PathVariable UUID id, @RequestBody RegistrarPagoRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        var comando = new RegistrarPagoUseCase.Comando(body.periodoDesde(), body.periodoHasta(), body.monto(), body.medio(), body.fechaDePago(), body.referencia(), body.nota(),
                Boolean.TRUE.equals(body.extenderVencimiento()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(PagoResponse.de(registrar.registrar(actor, id, comando))));
    }
}
