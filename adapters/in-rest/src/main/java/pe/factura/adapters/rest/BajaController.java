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
        Anulación ante SUNAT de una factura, boleta o nota ya **aceptada**, dentro de los 7 días calendario siguientes a su emisión
        (regla 2957). khipu genera la comunicación de baja (`VoidedDocuments`, `RA-yyyymmdd-N`) o, para una boleta, el resumen
        diario con la boleta en estado 3 (`SummaryDocuments`, `RC-yyyymmdd-N`), la firma y la envía con `sendSummary`; SUNAT
        devuelve un ticket y el resultado (CDR) se recoge con `getStatus` — normalmente en la misma llamada. Si SUNAT sigue
        procesando, la baja queda `ENVIADA` y khipu la consulta cada 30 s; al aceptarse, el comprobante pasa a `ANULADO` y su
        número no se reutiliza.""")
public class BajaController {
    private final DarDeBajaUseCase bajas;

    @PostMapping("/v1/facturas/{id}/baja")
    @Operation(summary = "Dar de baja un comprobante", description = """
            Solo comprobantes `ACEPTADO` o `ACEPTADO_CON_OBS` (facturas, boletas y notas) emitidos hace 7 días calendario o menos,
            contados en hora de Lima desde la fecha de emisión y no desde la aceptación de SUNAT (la regla que SUNAT aplica a las
            boletas y a sus notas desde 2027-01-01, RS 000048-2026; hasta entonces las cuenta desde la CDR, que nunca es anterior a
            la emisión, así que khipu cumple las dos): el día 7 se admite, el 8 no. SUNAT mide el plazo con el día en que recibe la
            comunicación: si el envío falla (`ERROR_ENVIO`) y el reintento le llega después del día 7, la rechaza con 2957 aunque
            khipu la haya admitido (el reintento reenvía el mismo `RA`/`RC`, con su fecha de generación original). Una baja
            en curso bloquea otra sobre el mismo comprobante. El motivo es obligatorio; en una boleta queda en khipu (el resumen
            diario no lo lleva). Una boleta solo se anula si no se entregó al adquirente (RS 097-2012, art. 14): si ya se entregó
            no corresponde la baja sino una nota de crédito. khipu no puede saberlo, así que no lo valida.

            **Errores**: `422 BAJA_INVALIDA` (no aceptado, fuera de plazo —2957—, motivo inválido, baja ya en curso),
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
    @Operation(summary = "Consultar una comunicación de baja", description = """
            Si está `ENVIADA`, consulta el ticket en SUNAT en el acto y devuelve el resultado actualizado; si está `GENERADA` o en `ERROR_ENVIO`, la reenvía.
            Un resumen diario con gratuitas generado hasta el 2026-12-31 que se reenvía desde el 2027-01-01 no se reenvía: queda `DESCARTADA` y la respuesta
            es el `RC` nuevo que lo reemplaza (otro `id`), con la fecha y el formato del día.""")
    public ApiResponse<BajaResponse> obtener(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(BajaResponse.de(bajas.continuar(TenantActual.id(req), id)));
    }
}
