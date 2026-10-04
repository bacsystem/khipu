package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ResumenDeComprobantesResponse;
import pe.factura.application.port.in.ResumirComprobantesUseCase;

import java.time.LocalDate;

/**
 * El resumen de los comprobantes de la empresa en un período (#15), para las métricas del portal: lo emitido, lo aceptado, lo que pide atención y lo facturado por moneda.
 * Se autentica como el resto de {@code /v1/facturas} (API key o JWT del portal con {@code X-Empresa}). Solo lectura.
 */
@RestController
@RequestMapping("/v1/facturas")
@Tag(name = "Facturas")
@RequiredArgsConstructor
public class FacturaResumenController {
    private final ResumirComprobantesUseCase resumir;

    @GetMapping("/resumen")
    @Operation(summary = "Resumen de comprobantes de un período", description = """
            Una foto de la empresa en un rango de fecha de emisión (`desde`/`hasta`, inclusive; un lado ausente deja el rango abierto de ese lado): cuántos comprobantes
            se **emitieron** (firmados, en cualquier estado), cuántos **aceptó SUNAT con su CDR**, cuántos piden **atención** (rechazados, con error de envío y fuera de plazo,
            cada clase por separado) y cuánto se **facturó por moneda**. Lo facturado cuenta lo aceptado y lo que está en camino, no lo rechazado, lo que se pasó del plazo
            ni lo dado de baja; las notas de crédito restan y las de débito suman. `desde > hasta` responde `400 RANGO_INVALIDO`; una fecha mal formada,
            `400 PARAMETRO_INVALIDO`.""")
    public ApiResponse<ResumenDeComprobantesResponse> resumen(HttpServletRequest req,
            @Parameter(description = "Fecha de emisión mínima, `YYYY-MM-DD` (inclusive)", example = "2026-09-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Fecha de emisión máxima, `YYYY-MM-DD` (inclusive)", example = "2026-09-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ApiResponse.ok(ResumenDeComprobantesResponse.de(resumir.resumir(TenantActual.id(req), desde, hasta)));
    }
}
