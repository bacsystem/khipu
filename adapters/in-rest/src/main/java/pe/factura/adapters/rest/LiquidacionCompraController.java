package pe.factura.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.adapters.rest.dto.LiquidacionCompraRequest;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.domain.documento.Comprobante;

@RestController
@RequestMapping("/v1/liquidaciones-compra")
@RequiredArgsConstructor
@Tag(name = "Liquidaciones de compra", description = """
        Liquidación de compra electrónica (tipo 04): la emite la empresa cuando **compra** a una persona sin RUC —un agricultor, un pescador artesanal, un
        recolector—, que no puede darle factura ni boleta. Se emite como una factura, en una sola llamada: khipu la numera en su serie `L###`, genera el
        `SelfBilledInvoice` UBL 2.1, lo firma, lo valida y lo envía a SUNAT. Consulta, XML, CDR, PDF y reenvío usan los mismos endpoints que las facturas
        (`GET /v1/facturas/{id}`, `/xml`, `/cdr`, `/pdf`, `POST /v1/facturas/{id}/enviar`); el listado la filtra con `tipo=04`.""")
public class LiquidacionCompraController {
    private static final String BASE = "/v1/facturas";
    private final EmitirComprobanteUseCase emitir;
    private final ObjectMapper json;

    @PostMapping
    @Operation(summary = "Emitir una liquidación de compra", description = """
            **Quién es quién**: la empresa es la compradora y la emisora; el `vendedor` es una persona sin RUC con DNI (`1`), carné de extranjería (`4`) o
            pasaporte (`7`), y su domicilio completo (dirección, ubigeo y condición del catálogo 60) es obligatorio, igual que el `lugar_operacion`. Un vendedor
            con RUC responde `422 RECEPTOR_INVALIDO` (2800): emite su propia factura o boleta.

            **Retenciones**: el comprador retiene al vendedor el IGV de las líneas gravadas y el Impuesto a la Renta (1.5 % del valor de venta de cada línea, tributo
            3000), y los declara y paga en su lugar. Por eso `totales.total` es el **importe neto** que se paga al vendedor: subtotal (valor de venta + IGV) − IGV − renta
            + redondeo (regla 4312); el bloque `liquidacion` de la respuesta trae la retención y el subtotal. El `precio_unitario` de una línea gravada incluye el IGV,
            como en la factura.

            **Plazo**: SUNAT la recibe hasta el día calendario siguiente a su emisión (2108); no tiene resumen diario. **Baja**: SUNAT no la anula con una comunicación
            de baja (2308) sino con un resumen de reversiones, que khipu todavía no emite: `POST /v1/facturas/{id}/baja` responde `422 BAJA_INVALIDA`.

            **Aún no**: compra de oro (`0503`), compras gratuitas, otros tributos (9999) y anticipos. Cada línea lleva el código de producto SUNAT (obligatorio, 3506).
            Que el DNI exista, no sea de un menor ni de un fallecido (2450, 2460–2462) y que el vendedor no pase de 75 UIT al año (2451) lo valida SUNAT con datos que
            khipu no tiene: si falla, la liquidación queda `RECHAZADO` con el número consumido.

            **Errores**: `422 VALIDACION`, `422 RECEPTOR_INVALIDO` (vendedor), `422 LIQUIDACION_INVALIDA` (ubigeos, condiciones, lugar, tasa), `422 ITEM_INVALIDO`,
            `422 AFECTACION_INVALIDA`, `422 TIPO_OPERACION_INVALIDO`, `422 REDONDEO_INVALIDO` (4314: el redondeo pasa de ±1.00, o el neto queda en cero o negativo, 2062), `422 FECHA_INVALIDA`, `422 SERIE_INVALIDA`, `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`,
            `429 LIMITE_PLAN`, `402 PLAN_VENCIDO`. Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las liquidaciones.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Liquidación creada; `estado_documento` indica si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: la liquidación ya emitida, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Correlativo repetido en la serie (DUPLICADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody LiquidacionCompraRequest body,
            @Parameter(description = "Clave única por liquidación (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        var idempotencia = ClaveDeIdempotencia.de(clave, body, json);
        if (idempotencia == null) {
            Comprobante c = emitir.emitirLiquidacionCompra(TenantActual.id(req), body.aComando());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE)));
        }
        var emision = emitir.emitirLiquidacionCompra(TenantActual.id(req), body.aComando(), idempotencia);
        return ResponseEntity.status(emision.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(emision.comprobante(), BASE)));
    }

    @PostMapping("/calcular")
    @Operation(summary = "Calcular la liquidación de compra sin emitir", description = """
            Recibe el mismo cuerpo que `POST /v1/liquidaciones-compra` y responde los importes —IGV, retención de renta e importe neto— **sin numerar, firmar, guardar
            ni enviar nada**, con las mismas reglas de dominio: lo que la emisión rechazaría responde aquí el mismo `422`. El `id` de la respuesta no existe y no trae enlaces.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Importes calculados; no se guardó nada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de dominio incumplida, igual que en la emisión")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> calcular(HttpServletRequest req, @Valid @RequestBody LiquidacionCompraRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(ComprobanteResponse.de(emitir.calcularLiquidacionCompra(TenantActual.id(req), body.aComando()), null)));
    }
}
