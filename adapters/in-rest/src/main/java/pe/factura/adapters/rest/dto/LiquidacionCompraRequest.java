package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirLiquidacionCommand;
import pe.factura.domain.documento.CodigoProductoSunat;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.LiquidacionCompra;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Liquidación de compra (04, #327): la empresa compra a una persona sin RUC (agricultor, pescador artesanal, recolector) y le retiene el IGV y el Impuesto a la Renta. Los
 * registros anidados llevan nombre de esquema propio: springdoc nombra por nombre simple y {@code ItemDto} o {@code UbicacionDto} chocarían con los de otros DTO.
 */
public record LiquidacionCompraRequest(
        @NotBlank @Pattern(regexp = "L[A-Z0-9]{3}", message = "serie de liquidación de compra inválida (L + 3 alfanuméricos, p. ej. L001)") @Schema(example = "L001", description = "Serie registrada en `POST /v1/series` con el tipo `04`: `L` + 3 alfanuméricos (regla 1001)") String serie,
        @Positive @Schema(example = "15", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
        @NotNull @Schema(example = "2026-10-20", description = "Fecha de emisión (`YYYY-MM-DD`), no futura. SUNAT recibe la liquidación de compra hasta el día calendario siguiente (2108): con una fecha anterior responde `422 FECHA_INVALIDA` sin consumir número") LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") @Schema(example = "0501", description = "Tipo de operación, catálogo 51: `0501` compra interna (por defecto) o `0502` anticipos. El `0503` (compra de oro) todavía no: exige los datos del derecho minero (catálogo 55, reglas 2466–2469) y responde `422 TIPO_OPERACION_INVALIDO`") String tipoOperacion,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") @Schema(example = "PEN", description = "Moneda ISO 4217 (catálogo 02), las mismas que la factura. Otro código responde `422 MONEDA_INVALIDA` (3088)") String moneda,
        @NotNull @Valid @Schema(description = "Quien vende: una persona sin RUC con su domicilio") VendedorDto vendedor,
        @NotNull @Valid @Schema(description = "Lugar donde se realiza la operación (`cac:DeliveryTerms/cac:DeliveryLocation`): obligatorio (2454, 2455, 2458)") UbicacionDto lugarOperacion,
        @NotEmpty @Valid @ArraySchema(minItems = 1, maxItems = Comprobante.MAX_LINEAS, arraySchema = @Schema(description = "Lo que se compra: de 1 a 999 líneas (2023)")) List<@NotNull @Valid ItemDto> items,
        @Valid @Schema(description = "Retención del Impuesto a la Renta. Si se omite, la tasa vigente (1.5 %)") RetencionRentaDto retencionRenta,
        @Valid @ArraySchema(arraySchema = @Schema(description = "Guías de remisión del traslado (`09` remitente, `31` transportista). Opcional")) List<FacturaRequest.GuiaDto> guias,
        @Valid @ArraySchema(arraySchema = @Schema(description = "Otros documentos relacionados: en una liquidación de compra solo el tipo `99` (otros); otro tipo responde `422 DOCUMENTO_RELACIONADO_INVALIDO` (4009). Opcional")) List<FacturaRequest.DocumentoRelacionadoDto> documentosRelacionados,
        @Schema(example = "0.25", description = "Redondeo del importe neto (`PayableRoundingAmount`): entre −1.00 y 1.00 con 2 decimales (4314, 3303). Opcional") BigDecimal redondeo,
        @DecimalMin(value = "0.001") @Digits(integer = 3, fraction = 3) @Schema(example = "3.756", description = "Tipo de cambio: soles por unidad de `moneda` (#316). Opcional y solo fuera de `PEN`; no va al XML") BigDecimal tipoCambio,
        @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` la deja `FIRMADO` para enviarla con `POST /v1/facturas/{id}/enviar`") Boolean enviarAutomatico,
        @Size(max = 1000) @Schema(example = "Compra en chacra; pago en efectivo.", description = "Texto libre para el bloque «Observaciones» del PDF (hasta 1000 caracteres); no va al XML") String observaciones) {

    @Schema(name = "VendedorLiquidacionCompra")
    public record VendedorDto(
            @NotBlank @Pattern(regexp = "[147]", message = "documento del vendedor: 1 (DNI), 4 (carné de extranjería) o 7 (pasaporte)") @Schema(example = "1", description = "Catálogo 06: `1` DNI, `4` carné de extranjería o `7` pasaporte (2800). Quien tiene RUC (`6`) emite su propia factura o boleta") String tipoDoc,
            @NotBlank @Schema(example = "45678912", description = "DNI de 8 dígitos (2450); carné o pasaporte, hasta 15 caracteres sin espacios (2802)") String numDoc,
            @NotBlank @Schema(example = "JUAN QUISPE MAMANI", description = "Apellidos y nombres, hasta 1500 caracteres sin saltos de línea (2021, 2022)") String nombre,
            @NotBlank @Schema(example = "Caserío Santa Rosa s/n", description = "Dirección del domicilio del vendedor y lo que permita ubicarlo, de 3 a 250 caracteres (2452, 2593)") String direccion,
            @NotBlank @Pattern(regexp = "\\d{6}") @Schema(example = "060101", description = "Ubigeo INEI del domicilio (catálogo 13, `GET /v1/catalogos/13`; 2453, 4339)") String ubigeo,
            @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "02", description = "Condición del domicilio, catálogo 60: `01` punto de venta, `02` producción, `03` extracción, `04` explotación, `05` otros (2456, 2457)") String condicion) {}

    @Schema(name = "LugarDeOperacion")
    public record UbicacionDto(
            @NotBlank @Schema(example = "Fundo Santa Rosa, km 12 carretera a Bambamarca", description = "Dirección del lugar y lo que permita ubicarlo, de 3 a 250 caracteres (2454, 2778)") String direccion,
            @NotBlank @Pattern(regexp = "\\d{6}") @Schema(example = "060101", description = "Ubigeo INEI del lugar (catálogo 13; 2455, 2917)") String ubigeo,
            @NotBlank @Pattern(regexp = "\\d{2}") @Schema(example = "02", description = "Condición del lugar, catálogo 60: `01` punto de venta, `02` producción, `03` extracción, `04` explotación, `05` otros (2458, 2459)") String condicion) {
        LiquidacionCompra.Ubicacion aDominio() { return new LiquidacionCompra.Ubicacion(direccion, ubigeo, condicion); }
    }

    @Schema(name = "ItemLiquidacionCompra")
    public record ItemDto(
            @Schema(example = "CAF-01", description = "Código interno del producto (opcional, hasta 30 caracteres)") String codigo,
            @NotBlank @Schema(example = "Café pergamino seco", description = "Descripción del bien comprado (2026, 2027)") String descripcion,
            @NotBlank @Schema(example = "KGM", description = "Unidad de medida UN/ECE rec 20 (catálogo 03): `KGM` kilogramo, `NIU` unidad, `TNE` tonelada… (2936)") String unidad,
            @NotNull @Positive @Schema(example = "100", description = "Cantidad, hasta 10 decimales") BigDecimal cantidad,
            @NotNull @Schema(example = "11.80", minimum = "0", exclusiveMinimum = true, description = "Precio unitario: **con IGV incluido** en una línea gravada (`10`), como en la factura; khipu calcula el valor unitario, el IGV y la retención. Mayor que cero (2369)") BigDecimal precioUnitario,
            @NotBlank @Pattern(regexp = "10|20|30", message = "afectación del IGV en una liquidación de compra: 10, 20 o 30") @Schema(example = "20", description = "Afectación del IGV (catálogo 07): `10` gravado, `20` exonerado (p. ej. productos agrícolas del Apéndice I), `30` inafecto. Las gratuitas todavía no se emiten en una liquidación de compra") String tipoAfectacionIgv,
            @NotBlank @Pattern(regexp = "[0-9]{8}", message = "código de producto SUNAT de 8 dígitos (UNSPSC, catálogo 25)") @Schema(example = "50201706", description = "Código de producto SUNAT (catálogo 25, UNSPSC de 8 dígitos): **obligatorio** en la liquidación de compra (3506) y debe estar en el catálogo de SUNAT (3496)") String codigoSunat) {
        Item aDominio() {
            return new Item(codigo, descripcion, unidad, cantidad, precioUnitario, TipoAfectacionIgv.porCodigo(tipoAfectacionIgv), null, null, false, List.of(), CodigoProductoSunat.de(codigoSunat), null);
        }
    }

    public record RetencionRentaDto(
            @Positive @Digits(integer = 3, fraction = 5) @Schema(example = "1.5", description = "Tasa en porcentaje sobre el valor de venta de cada línea; hoy solo la vigente, 1.5 (otra responde `422 LIQUIDACION_INVALIDA`, 4237)") BigDecimal tasa) {}

    public EmitirLiquidacionCommand aComando() {
        return new EmitirLiquidacionCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(vendedor.tipoDoc(), vendedor.numDoc(), vendedor.nombre(), vendedor.direccion()),
                new LiquidacionCompra(vendedor.ubigeo(), vendedor.condicion(), lugarOperacion.aDominio(), retencionRenta == null ? null : retencionRenta.tasa()),
                items.stream().map(ItemDto::aDominio).toList(),
                new Referencias(null, guias == null ? List.of() : guias.stream().map(FacturaRequest.GuiaDto::aDominio).toList(),
                        documentosRelacionados == null ? List.of() : documentosRelacionados.stream().map(FacturaRequest.DocumentoRelacionadoDto::aDominio).toList()),
                redondeo, tipoCambio, enviarAutomatico == null || enviarAutomatico, observaciones);
    }
}
