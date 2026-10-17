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
import pe.factura.adapters.rest.dto.NotaRequest;
import pe.factura.application.port.in.EmitirComprobanteUseCase;
import pe.factura.application.port.in.ValidarCompradorUseCase;
import pe.factura.domain.documento.Comprobante;

import java.util.List;

@RestController
@RequestMapping("/v1/notas")
@RequiredArgsConstructor
@Tag(name = "Notas de crédito y débito", description = """
        Notas de crédito (07) y de débito (08) sobre facturas y boletas de la empresa. Una nota se emite como una factura: khipu la
        numera en su serie, genera el `CreditNote`/`DebitNote` UBL 2.1, lo firma, lo valida y lo envía a SUNAT en la misma
        llamada. Consulta, XML, CDR y reenvío usan los mismos endpoints que las facturas (`GET /v1/facturas/{id}` etc.), y la
        factura o boleta modificada lista sus notas en `notas`.""")
public class NotaController {
    private final EmitirComprobanteUseCase emitir;
    private final ObjectMapper json;
    private final ValidarCompradorUseCase comprador;

    @PostMapping
    @Operation(summary = "Emitir una nota de crédito o débito", description = """
            Sobre una factura o una boleta **aceptada** por SUNAT de la misma empresa. La nota toma el receptor, la moneda y el tipo de
            operación del documento; la serie debe existir con el tipo 07/08 y empezar con la letra del documento que modifica:
            `F` sobre una factura (p. ej. `FC01`, `FD01`), `B` sobre una boleta (p. ej. `BC01`, `BD01`; reglas 2399, 2400).

            **RUC del comprador**: si la nota lleva un RUC (el del documento que modifica), se valida contra el padrón de SUNAT como en la
            factura: si no figura, `422 RECEPTOR_INVALIDO` (1083) antes de numerar; si está de baja (4013) o no habido (4014), se emite igual
            y la respuesta trae `avisos`.

            **Sobre una boleta**: el comprador es el de la boleta (puede no tener documento). No se admiten la NC `13` (la boleta no
            tiene cuotas, regla 3259) ni la NC `04`, `05` u `08`: el descuento o la bonificación a un consumidor final va en la misma
            boleta (RCP art. 10 num. 1.4). La nota se envía sola hasta el 5.º día; el 6.º y el 7.º va en el resumen diario (como la boleta),
            salvo la NC `10` y la ND `03`, que no van en él y vencen el 5.º. Se anula con `POST /v1/facturas/{id}/baja`, que la informa en un
            resumen diario.

            **Nota total** (anulación `01`, devolución total `06`): omita `items` y khipu copia los ítems, el descuento global y
            los cargos de la factura (no envíe `descuento_global` ni `cargos` propios: se rechazan). Si la factura regularizó
            anticipos, indique los `items` por el importe neto: los anticipos no viajan en una nota (si el neto es 0.00 no hay nota posible:
            anule la factura con la comunicación de baja). **Nota parcial** (devolución
            por ítem `07`, descuento `04`/`05`, disminución `09`…): envíe los `items` que cubre, con el mismo formato que en la factura.

            **Nota de crédito 13** (corrección de cuotas): solo sobre facturas al crédito; envíe `forma_pago` al crédito con el
            neto pendiente y las cuotas corregidas (reglas 3257, 3260, 3320, 3321); la nota sale con importe 0 (regla 3315). En cualquier
            otra nota `forma_pago` se rechaza.

            **Límites** (notas de crédito): el total y las bases por tributo no pueden superar los del documento (reglas 3286, 3503;
            sobre una boleta el total admite S/ 1 de más). khipu descuenta además lo ya acreditado por las notas de crédito anteriores
            sobre el mismo documento (las rechazadas no cuentan): varias notas parciales pueden sumar el documento, pero una segunda nota
            total —o cualquiera que exceda el saldo— se rechaza aunque SUNAT, que compara nota por nota, la aceptaría.

            **Errores**: `422 NOTA_INVALIDA` (factura o boleta inexistente, no aceptada o anulada —2119/2120—, fecha anterior al documento
            —2885—, motivo fuera del catálogo —2172— o no admitido sobre una boleta —3259/4367—, importes mayores que el documento o que su
            saldo tras otras notas —3286/3503—), `422 FECHA_INVALIDA` (plazo de envío vencido —2108/1079—), `422 SERIE_INVALIDA`,
            `422 SERIE_NO_CONFIGURADA`, `409 DUPLICADO`, `429 LIMITE_PLAN` (la cuenta llegó al tope de documentos del mes de su plan: una nota
            consume como una factura; para anular sin consumir está la comunicación de baja), `402 PLAN_VENCIDO` (el plan de la cuenta
            venció y pasó su gracia: se vuelve a emitir al renovarlo; la comunicación de baja sigue).

            **Reintentos seguros** (`Idempotency-Key`): si la red se corta después de enviar el pedido, no se sabe si la nota se
            emitió, y repetirlo sin más emitiría otra y consumiría otro número. Mande una clave única por nota (un UUID) en la cabecera
            `Idempotency-Key` y repita el mismo pedido con la misma clave: si ya se había emitido, responde `200` con **la misma nota**
            (mismo id y número), sin emitir otra ni consumir número, aunque entretanto el documento haya pasado a tener una baja o vencido
            el plazo. La misma clave con otro contenido responde `422 IDEMPOTENCIA_INVALIDA`. La clave vale para la empresa durante 24 horas
            y es propia de las notas: la misma cadena en `POST /v1/facturas` es otra clave. Sin la cabecera, cada pedido es una nota nueva.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Nota creada; `estado_documento` indica si SUNAT ya la aceptó y `nota` trae la factura modificada y el motivo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pedido repetido con la misma `Idempotency-Key`: la nota ya emitida, en su estado actual"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Datos inválidos o regla de negocio incumplida; `codigo` y `mensaje` explican cuál")})
    public ResponseEntity<ApiResponse<ComprobanteResponse>> crear(HttpServletRequest req, @Valid @RequestBody NotaRequest body,
            @Parameter(description = "Clave única por nota (un UUID), para repetir el pedido sin emitir dos veces", example = "5f0c1d2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f")
            @RequestHeader(name = ClaveDeIdempotencia.CABECERA, required = false) String clave) {
        var idempotencia = ClaveDeIdempotencia.de(clave, body, json);
        if (idempotencia == null) {
            Comprobante c = emitir.emitirNota(TenantActual.id(req), body.aComando());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(c, "/v1/facturas"), avisos(c)));
        }
        var emision = emitir.emitirNota(TenantActual.id(req), body.aComando(), idempotencia);
        return ResponseEntity.status(emision.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(ApiResponse.ok(ComprobanteResponse.de(emision.comprobante(), "/v1/facturas"), avisos(emision.comprobante())));
    }

    /** #378: la nota lleva el comprador del documento que modifica, y SUNAT le aplica la misma regla del RUC (4013, 4014). */
    private List<ApiResponse.Aviso> avisos(Comprobante c) {
        return ApiResponse.Aviso.de(comprador.avisosDe(c.receptor()));
    }
}
