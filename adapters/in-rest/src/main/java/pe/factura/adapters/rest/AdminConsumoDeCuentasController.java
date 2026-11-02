package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ConsumoDeCuentasResponse;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.domain.plan.UsoDeLimite;

import java.time.YearMonth;

/**
 * El consumo de todas las cuentas contra el límite de su plan, con alertas de «cerca del límite» y «plan vencido», y su exportación (#193). Autenticado por
 * {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador. Solo lectura.
 */
@RestController
@RequestMapping("/v1/admin/consumo")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminConsumoDeCuentasController {
    private static final String CSV = "text/csv;charset=UTF-8";

    private final ConsultarConsumoDeCuentasUseCase consumo;

    @GetMapping
    @Operation(summary = "Consumo de todas las cuentas contra su plan", description = """
            Una fila por cuenta (las dadas de baja no salen): lo que consumió en el mes —solo comprobantes que SUNAT aceptó, por fecha de emisión, en el mes calendario de
            America/Lima, igual que el consumo de una cuenta— y el tope de su plan. **Se compara con el plan de hoy**, con los límites que rigen hoy, también si el mes es
            pasado. `porcentaje` es el del tope usado, hacia abajo, y falta si el plan no tiene tope; `en_alerta` es que ya llegó a `umbral_de_alerta` (inclusive).
            `estado_del_plan` es el de pago de su suscripción vigente: `VIGENTE`, `EN_GRACIA` (venció pero aún se la sirve) o `VENCIDA`.
            `filtro`: `TODAS` (por defecto), `CERCA_DEL_LIMITE` (en alerta) o `PLAN_VENCIDO` (venció su pago, en gracia o ya sin servicio). `orden`: `PORCENTAJE` (por defecto,
            de mayor a menor, los planes sin tope al final) o `DOCUMENTOS`. `mes` es `AAAA-MM`; sin él, el mes en curso. Paginado; el total, que refleja el filtro, va en
            `X-Total-Count`.""")
    public ResponseEntity<ApiResponse<ConsumoDeCuentasResponse>> listar(
            @Parameter(description = "Mes, `AAAA-MM`; por defecto el mes en curso", example = "2026-10") @RequestParam(required = false) String mes,
            @Parameter(description = "`TODAS` (por defecto), `CERCA_DEL_LIMITE` o `PLAN_VENCIDO`") @RequestParam(required = false) FiltroDeConsumo filtro,
            @Parameter(description = "`PORCENTAJE` (por defecto) o `DOCUMENTOS`") @RequestParam(required = false) OrdenDeConsumo orden,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        YearMonth elMes = elMes(mes);
        var filas = consumo.listar(elMes, filtro, orden, Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)));
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(consumo.contar(elMes, filtro)))
                .body(ApiResponse.ok(ConsumoDeCuentasResponse.de(elMes, UsoDeLimite.UMBRAL_DE_ALERTA, filas)));
    }

    @GetMapping("/exportacion")
    @Operation(summary = "Exportar el consumo de todas las cuentas", description = """
            Lo mismo que el listado, completo y sin paginar, como CSV (UTF-8 con marca de orden de bytes, registros separados por CRLF) que se descarga como
            `consumo-AAAA-MM.csv`. Respeta `mes`, `filtro` y `orden`. Un plan sin tope dice `ilimitado` y no tiene porcentaje; las fechas (`pagado_hasta`, `se_sirve_hasta`) son el último día cubierto en hora de Lima (`AAAA-MM-DD`), como en la pantalla. Los textos que empiezan
            por `=`, `+`, `-` o `@` llevan una comilla simple delante para que una hoja de cálculo no los ejecute como fórmula.""")
    public ResponseEntity<String> exportar(
            @Parameter(description = "Mes, `AAAA-MM`; por defecto el mes en curso", example = "2026-10") @RequestParam(required = false) String mes,
            @Parameter(description = "`TODAS` (por defecto), `CERCA_DEL_LIMITE` o `PLAN_VENCIDO`") @RequestParam(required = false) FiltroDeConsumo filtro,
            @Parameter(description = "`PORCENTAJE` (por defecto) o `DOCUMENTOS`") @RequestParam(required = false) OrdenDeConsumo orden) {
        YearMonth elMes = elMes(mes);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("consumo-" + elMes + ".csv").build().toString())
                .body(CsvDeConsumo.de(consumo.todas(elMes, filtro, orden)));
    }

    /** El mes pedido, o el en curso; se resuelve aquí una vez para que lo que se dice sea lo que se midió. */
    private YearMonth elMes(String mes) {
        YearMonth pedido = ParametroMes.de(mes);
        return pedido == null ? consumo.mesActual() : pedido;
    }
}
