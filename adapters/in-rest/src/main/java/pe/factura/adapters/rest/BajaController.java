package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.BajaRequest;
import pe.factura.adapters.rest.dto.BajaResponse;
import pe.factura.application.port.in.DarDeBajaUseCase;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Comunicación de baja", description = """
        Anulación ante SUNAT de una factura o nota ya **aceptada**, dentro de los 7 días calendario siguientes a su emisión
        (regla 2957). khipu genera el `VoidedDocuments` (`RA-yyyymmdd-N`), lo firma y lo envía con `sendSummary`; SUNAT devuelve
        un ticket y el resultado (CDR) se recoge con `getStatus` — normalmente en la misma llamada. Si SUNAT sigue procesando,
        la baja queda `ENVIADA` y khipu la consulta cada 30 s; al aceptarse, el comprobante pasa a `ANULADO` y su número no
        se reutiliza. Las boletas se anulan en el resumen diario, no aquí.""")
public class BajaController {
    private final DarDeBajaUseCase bajas;

    @PostMapping("/v1/facturas/{id}/baja")
    @Operation(summary = "Dar de baja un comprobante", description = """
            Solo comprobantes `ACEPTADO` o `ACEPTADO_CON_OBS` (facturas y notas) emitidos hace 7 días o menos. Una baja en
            curso bloquea otra sobre el mismo comprobante.

            **Errores**: `422 BAJA_INVALIDA` (no aceptado, fuera de plazo —2957—, boleta, motivo inválido, baja ya en curso),
            `404 NO_ENCONTRADO`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comunicación creada; `estado` indica si SUNAT ya la aceptó (`ACEPTADA`) o sigue en proceso (`ENVIADA`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "El comprobante no puede darse de baja; `mensaje` lleva la regla SUNAT")})
    public ResponseEntity<ApiResponse<BajaResponse>> solicitar(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody BajaRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(BajaResponse.de(bajas.solicitar(TenantActual.id(req), id, body.motivo()))));
    }

    @GetMapping("/v1/facturas/{id}/bajas")
    @Operation(summary = "Comunicaciones de baja de un comprobante", description = "De la más reciente a la más antigua; normalmente una.")
    public ApiResponse<List<BajaResponse>> deComprobante(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(bajas.deComprobante(TenantActual.id(req), id).stream().map(BajaResponse::de).toList());
    }

    @GetMapping("/v1/bajas/{id}")
    @Operation(summary = "Consultar una comunicación de baja", description = "Si está `ENVIADA`, consulta el ticket en SUNAT en el acto y devuelve el resultado actualizado.")
    public ApiResponse<BajaResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(BajaResponse.de(bajas.continuar(TenantActual.id(req), id)));
    }
}
