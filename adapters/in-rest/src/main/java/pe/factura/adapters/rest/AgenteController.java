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
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.AgenteRequest;
import pe.factura.adapters.rest.dto.ComprobanteResponse;
import pe.factura.application.port.in.EmitirAgenteCommand;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.domain.documento.Comprobante;

/**
 * Comprobantes de retención (20) y de percepción (40), #40: los emite la empresa que SUNAT designó agente. Un endpoint por tipo, con su cuerpo y su espacio de claves de
 * idempotencia; el resto (consulta, XML, CDR, PDF, reenvío, reversión) es el de los demás comprobantes.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Retenciones y percepciones", description = """
        Comprobantes de retención (tipo 20) y de percepción (tipo 40) del IGV: solo para la empresa que **SUNAT designó agente** de retención o de percepción, y que khipu habilitó
        después de verificar la resolución de designación (sin ella: `422 AGENTE_NO_DESIGNADO`, también al crear la serie `R###` o `P###`). Se emiten en una sola llamada: khipu los
        numera en su serie, genera el `Retention` o `Perception` UBL 2.0, lo firma, lo valida contra el XSD de SUNAT y lo envía al servicio de retenciones y percepciones de SUNAT.
        Consulta, XML, CDR, PDF y reenvío usan los mismos endpoints que las facturas (`GET /v1/facturas/{id}`, `/xml`, `/cdr`, `/pdf`, `POST /v1/facturas/{id}/enviar`); el listado los
        filtra con `tipo=20` o `tipo=40` y no suman a lo facturado. **Reversión**: `POST /v1/facturas/{id}/baja` los revierte en un resumen de reversiones (`RR-…`) de SUNAT: SUNAT no lo valida contra la emisión; la norma exige presentarlo dentro de 7 días calendario desde que se comunicó la reversión.

        **Importes**: lo retenido o percibido y el neto de cada documento son siempre en **soles**: un documento en otra moneda lleva su `tipo_cambio` y el pago se convierte con él.
        `totales.total` es el importe total retenido o percibido; el bloque `agente` de la respuesta trae además cada documento y el importe total pagado o cobrado.

        **Lo que valida SUNAT con datos que khipu no tiene**: que la empresa sea agente (2617 en la retención; en la percepción solo lo observa, 4285), que el RUC del proveedor exista
        (2621), que el comprobante cobrado en una percepción exista en SUNAT (2609), sea al crédito (3329) y no tenga ya percepción (3312), y la fecha de su emisión (2610). e-beta no
        aplica ninguna de estas reglas. Un rechazo de SUNAT **no consume el número** en estos documentos (manual del programador): khipu sí lo da por usado y se corrige emitiendo
        otro con el siguiente.

        **Aún no**: la nota de crédito y el propio 20/40 como documento relacionado (los que la hoja deja sin pago ni totales), la emisión excepcional de la percepción
        (`sac:ExceptionalIndicator`), los comprobantes de contingencia y el redondeo del total (`PayableRoundingAmount`: e-beta responde 0306 a todo XML que lo lleva).""")
public class AgenteController {
    private static final String BASE = "/v1/facturas";
    private static final String ERRORES = """
            **Errores**: `422 VALIDACION`, `422 AGENTE_NO_DESIGNADO` (2617/4285), `422 RECEPTOR_INVALIDO` (contraparte), `422 RETENCION_INVALIDA` o `422 PERCEPCION_INVALIDA` (régimen y tasa,
            documentos, pagos, fechas, tipo de cambio; con el código de la regla de SUNAT), `422 FECHA_INVALIDA` (futura o pasados los 7 días, 2600),
            `422 SERIE_INVALIDA`, `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`, `429 LIMITE_PLAN`, `402 PLAN_VENCIDO`.""";
    private final EmitirComprobanteUseCase emitir;
    private final ObjectMapper json;

    @PostMapping("/v1/retenciones")
    @Operation(summary = "Emitir un comprobante de retención", description = """
            El agente de retención paga al `proveedor` (siempre con RUC) los `documentos` que este le emitió y le retiene el IGV a la tasa del `regimen` (catálogo 23: `01` 3 %, el vigente;
            `02` 6 %, solo para comprobantes emitidos hasta el 28/02/2014). Por documento: retenido = pago × tipo de cambio × tasa, y neto pagado = pago × tipo de cambio − retenido. Todos
            los pagos son del mes del comprobante (2625). SUNAT lo recibe hasta 7 días calendario desde el día siguiente a su emisión (2600).

            """ + ERRORES + " Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las retenciones.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Retención creada; `estado_documento` indica si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: la retención ya emitida, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Correlativo repetido en la serie (DUPLICADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos, empresa no designada o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> retener(HttpServletRequest req, @Valid @RequestBody AgenteRequest.Retencion body,
            @Parameter(description = "Clave única por retención (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        return emitir(req, body.aComando(), ClaveDeIdempotencia.de(clave, body, json));
    }

    @PostMapping("/v1/retenciones/calcular")
    @Operation(summary = "Calcular un comprobante de retención sin emitirlo", description = """
            Recibe el mismo cuerpo que `POST /v1/retenciones` y responde lo retenido y el neto de cada documento y los totales **sin numerar, firmar, guardar ni enviar nada**, con las
            mismas reglas de dominio (y la misma exigencia de la designación): lo que la emisión rechazaría responde aquí el mismo `422`. El `id` de la respuesta no existe y no trae enlaces.""")
    public ResponseEntity<ApiResponse<ComprobanteResponse>> calcularRetencion(HttpServletRequest req, @Valid @RequestBody AgenteRequest.Retencion body) {
        return calcular(req, body.aComando());
    }

    @PostMapping("/v1/percepciones")
    @Operation(summary = "Emitir un comprobante de percepción", description = """
            El agente de percepción cobra al `cliente` los `documentos` que le emitió y le percibe el IGV a la tasa del `regimen` (catálogo 22: `01` venta interna 2 %, `02` combustible 1 %,
            `03` tasa especial 0.5 %). Por documento: percibido = cobro × tipo de cambio × tasa, y neto cobrado = cobro × tipo de cambio + percibido. Todos los cobros son del mes del
            comprobante (2612). SUNAT lo recibe hasta 7 días calendario desde el día siguiente a su emisión (2600). Si la percepción ya se informó en la factura (tipo de operación 2001), no se emite
            además este comprobante: SUNAT lo rechazaría (3312).

            """ + ERRORES + " Admite `Idempotency-Key` como `POST /v1/facturas`, con claves propias de las percepciones.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Percepción creada; `estado_documento` indica si SUNAT ya la aceptó"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: la percepción ya emitida, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Correlativo repetido en la serie (DUPLICADO)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos, empresa no designada o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> percibir(HttpServletRequest req, @Valid @RequestBody AgenteRequest.Percepcion body,
            @Parameter(description = "Clave única por percepción (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        return emitir(req, body.aComando(), ClaveDeIdempotencia.de(clave, body, json));
    }

    @PostMapping("/v1/percepciones/calcular")
    @Operation(summary = "Calcular un comprobante de percepción sin emitirlo", description = """
            Recibe el mismo cuerpo que `POST /v1/percepciones` y responde lo percibido y el neto de cada documento y los totales **sin numerar, firmar, guardar ni enviar nada**, con las
            mismas reglas de dominio (y la misma exigencia de la designación). El `id` de la respuesta no existe y no trae enlaces.""")
    public ResponseEntity<ApiResponse<ComprobanteResponse>> calcularPercepcion(HttpServletRequest req, @Valid @RequestBody AgenteRequest.Percepcion body) {
        return calcular(req, body.aComando());
    }

    private ResponseEntity<ApiResponse<ComprobanteResponse>> emitir(HttpServletRequest req, EmitirAgenteCommand cmd, pe.factura.application.port.in.Idempotencia idempotencia) {
        if (idempotencia == null) {
            Comprobante c = emitir.emitirDeAgente(TenantActual.id(req), cmd);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, BASE)));
        }
        var emision = emitir.emitirDeAgente(TenantActual.id(req), cmd, idempotencia);
        return ResponseEntity.status(emision.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(emision.comprobante(), BASE)));
    }

    private ResponseEntity<ApiResponse<ComprobanteResponse>> calcular(HttpServletRequest req, EmitirAgenteCommand cmd) {
        return ResponseEntity.ok(ApiResponse.ok(ComprobanteResponse.de(emitir.calcularDeAgente(TenantActual.id(req), cmd), null)));
    }
}
