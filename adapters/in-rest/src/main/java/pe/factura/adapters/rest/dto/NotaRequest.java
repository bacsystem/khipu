package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirNotaCommand;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.TipoDocumento;

import java.time.LocalDate;
import java.util.List;

/**
 * Nota de crédito (07) o de débito (08) sobre una factura o una boleta aceptada de la misma empresa (#293). Receptor, moneda y tipo de operación
 * se toman del documento; sin `items` la nota es total (copia ítems, descuento global y cargos del documento).
 */
public record NotaRequest(
        @NotBlank @Pattern(regexp = "07|08", message = "tipo de nota: 07 (crédito) u 08 (débito)") @Schema(example = "07", description = "`07` nota de crédito, `08` nota de débito (catálogo 01)") String tipo,
        @NotBlank @Pattern(regexp = "[FB][A-Z0-9]{3}", message = "serie de nota inválida (F o B + 3 alfanuméricos, p. ej. FC01 o BC01)") @Schema(example = "FC01", description = "Serie de la nota, registrada en `POST /v1/series` con el tipo 07/08. Empieza con la letra del documento que modifica (reglas 1001, 2399, 2400): `F` sobre una factura (`FC01`), `B` sobre una boleta (`BC01`)") String serie,
        @Positive @Schema(example = "4", description = "Número correlativo. Omítalo para que khipu asigne el siguiente (recomendado)") Long correlativo,
        @NotNull @Schema(example = "2026-09-18", description = "Fecha de emisión, no futura y no anterior a la del documento que modifica (regla 2885). Sobre una factura, se envía hasta el 3.er día; sobre una boleta, sola hasta el 5.º y en el resumen diario hasta el 7.º (la NC `10` y la ND `03` no van en el resumen: hasta el 5.º)") LocalDate fechaEmision,
        @NotNull @Valid @Schema(description = "Factura o boleta que se modifica; debe estar ACEPTADA por SUNAT y no anulada (reglas 2119, 2120)") DocumentoAfectadoDto documentoAfectado,
        @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "01", description = "Motivo: catálogo 09 para notas de crédito (`01` anulación, `06` devolución total, `07` devolución por ítem, `13` corrección de cuotas…), catálogo 10 para débito (`01` intereses por mora, `02` aumento en el valor, `03` penalidades…). `GET /v1/catalogos/09` y `/10`. Sobre una boleta no se admiten la NC `13` (regla 3259) ni la NC `04`, `05` u `08`: el descuento o la bonificación a un consumidor final va en la misma boleta (RCP art. 10 num. 1.4)") String motivo,
        @NotBlank @Size(max = 500) @Schema(example = "El cliente devolvió la mercadería completa", description = "Sustento de la nota (1–500 caracteres, sin saltos de línea; regla 2135)") String descripcion,
        @Valid @ArraySchema(maxItems = Comprobante.MAX_LINEAS, arraySchema = @Schema(description = "Ítems que cubre la nota, con el mismo formato (y las mismas reglas de línea: ICBPER, precio mayor que cero) que en la factura, hasta 999 (`422 ITEM_INVALIDO`, regla `2137`). **Omítalos** para una nota total: khipu copia los ítems, el descuento global y los cargos de la factura (anulación, devolución total). Obligatorios si la factura regularizó anticipos (la nota va por el importe neto; si el neto es 0.00 no hay nota posible y se anula con la comunicación de baja)")) List<FacturaRequest.@NotNull @Valid ItemDto> items,
        @Valid @Schema(description = "Descuento global de la nota. Solo con `items`: sin ellos se rechaza (`NOTA_INVALIDA`) porque la nota total copia el de la factura") FacturaRequest.DescuentoDto descuentoGlobal,
        @Valid @Schema(description = "Cargos globales de la nota. Solo con `items`: sin ellos se rechaza (`NOTA_INVALIDA`) porque la nota total copia los de la factura") List<FacturaRequest.CargoDto> cargos,
        @Valid @Schema(description = "Solo en una nota de crédito con motivo `13` (en cualquier otra nota se rechaza con `NOTA_INVALIDA`): la forma de pago al crédito con el neto pendiente y las cuotas corregidas de la factura (reglas 3257, 3320, 3321). Esta nota no mueve importes: khipu genera una única línea de valor 0 (regla 3315) e ignora `items`") FacturaRequest.FormaPagoDto formaPago,
        @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` deja la nota `FIRMADO` para enviarla con `POST /v1/facturas/{id}/enviar`") Boolean enviarAutomatico,
        @Size(max = 1000) @Schema(example = "Devolución recibida en tienda.", description = "Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML") String observaciones) {

    public record DocumentoAfectadoDto(
            @NotBlank @Pattern(regexp = "[FB][A-Z0-9]{3}", message = "serie de factura (F###) o boleta (B###) inválida") @Schema(example = "F001", description = "Serie de la factura (`F…`) o boleta (`B…`) que se modifica") String serie,
            @NotNull @Positive @Schema(example = "123") Long numero) {}

    public EmitirNotaCommand aComando() {
        List<Item> its = items == null ? null : items.stream().map(FacturaRequest.ItemDto::aDominio).toList();
        return new EmitirNotaCommand(TipoDocumento.porCodigo(tipo), serie, correlativo, fechaEmision, documentoAfectado.serie(), documentoAfectado.numero(), motivo, descripcion,
                its, descuentoGlobal == null ? null : descuentoGlobal.aDominio(),
                FacturaRequest.cargos(cargos, true),
                formaPago == null ? null : formaPago.aDominio(), enviarAutomatico == null || enviarAutomatico, observaciones);
    }
}
