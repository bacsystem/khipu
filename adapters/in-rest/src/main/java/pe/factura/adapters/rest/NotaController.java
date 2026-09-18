package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.adapters.rest.dto.NotaRequest;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.domain.documento.Comprobante;

@RestController
@RequestMapping("/v1/notas")
@RequiredArgsConstructor
@Tag(name = "Notas de crédito y débito", description = """
        Notas de crédito (07) y de débito (08) sobre facturas de la empresa. Una nota se emite como una factura: khipu la
        numera en su serie, genera el `CreditNote`/`DebitNote` UBL 2.1, lo firma, lo valida y lo envía a SUNAT en la misma
        llamada. Consulta, XML, CDR y reenvío usan los mismos endpoints que las facturas (`GET /v1/facturas/{id}` etc.), y la
        factura modificada lista sus notas en `notas`.""")
public class NotaController {
    private final EmitirComprobanteUseCase emitir;

    @PostMapping
    @Operation(summary = "Emitir una nota de crédito o débito", description = """
            Sobre una factura **aceptada** por SUNAT de la misma empresa. La nota toma el receptor, la moneda y el tipo de
            operación de la factura; la serie debe existir con el tipo 07/08 y empezar por `F` (p. ej. `FC01`, `FD01`).

            **Nota total** (anulación `01`, devolución total `06`): omita `items` y khipu copia los ítems, el descuento global y
            los cargos de la factura. **Nota parcial** (devolución por ítem `07`, descuento `04`/`05`, disminución `09`…):
            envíe los `items` que cubre, con el mismo formato que en la factura.

            **Nota de crédito 13** (corrección de cuotas): solo sobre facturas al crédito; envíe `forma_pago` al crédito con el
            neto pendiente y las cuotas corregidas (reglas 3257, 3260, 3320, 3321); la nota sale con importe 0 (regla 3315).

            **Límites** (notas de crédito): el total y las bases por tributo no pueden superar los de la factura (reglas 3286, 3503).

            **Errores**: `422 NOTA_INVALIDA` (factura inexistente, no aceptada o anulada —2119/2120—, fecha anterior a la factura
            —2885—, motivo fuera del catálogo —2172—, importes mayores que la factura —3286/3503—), `422 SERIE_INVALIDA`,
            `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Nota creada; `estado_documento` indica si SUNAT ya la aceptó y `nota` trae la factura modificada y el motivo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody NotaRequest body) {
        Comprobante c = emitir.emitirNota(TenantActual.id(req), body.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, "/v1/facturas")));
    }
}
