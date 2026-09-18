package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaRequest(
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") @Schema(example = "F001", description = "Serie de factura: `F` + 3 alfanuméricos, registrada previamente en `POST /v1/series`") String serie,
        @Positive @Schema(example = "125", description = "Número correlativo. Omítalo para que khipu asigne el siguiente de la serie (recomendado); si lo envía y ya existe responde `409 DUPLICADO`") Long correlativo,
        @NotNull @Schema(example = "2026-09-14", description = "Fecha de emisión (`YYYY-MM-DD`), no futura. SUNAT exige recibir la factura dentro de los 3 días calendario siguientes") LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") @Schema(example = "0101", description = "Tipo de operación, catálogo 51 (`GET /v1/catalogos/51`). `0101` venta interna (por defecto), `0200` exportación, `1001` operación sujeta a detracción") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") @Schema(example = "PEN", description = "Moneda ISO 4217 de todo el comprobante: `PEN`, `USD` o `EUR` (catálogo 02)") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        @Valid @Schema(description = "Forma de pago (RS 193-2020). Si se omite, al contado.") FormaPagoDto formaPago,
        @Valid @Schema(description = "Descuento sobre el total (catálogo 53: `02` si afecta la base del IGV —requiere ítems gravados—, `03` si no). Opcional.") DescuentoDto descuentoGlobal,
        @Schema(example = "true", description = "`true` (por defecto) envía a SUNAT en la misma llamada; `false` deja el comprobante `FIRMADO` para enviarlo luego con `POST /v1/facturas/{id}/enviar` (p. ej. para emitir en lote y enviar después)") Boolean enviarAutomatico) {

    public record ClienteDto(
            @NotBlank @Schema(example = "6", description = "Tipo de documento de identidad, catálogo 06. En factura debe ser `6` (RUC); `1` DNI, `4` carné de extranjería y `7` pasaporte se usan en boletas") String tipoDoc,
            @NotBlank @Schema(example = "20123456789", description = "RUC de 11 dígitos del adquirente") String numDoc,
            @NotBlank @Schema(example = "Comercial Andina SAC", description = "Razón social tal como figura en la ficha RUC del adquirente") String razonSocial,
            @Schema(example = "Av. Javier Prado Este 123, San Isidro", description = "Dirección del adquirente (opcional; se imprime en el XML)") String direccion) {}

    public record ItemDto(
            @Schema(example = "SKU-001", description = "Código interno del producto o servicio (opcional)") String codigo,
            @NotBlank @Schema(example = "Servicio de consultoría", description = "Descripción detallada del bien o servicio") String descripcion,
            @NotBlank @Schema(example = "NIU", description = "Unidad de medida, catálogo 03 (`GET /v1/catalogos/03`): `NIU` unidad (bienes), `ZZ` unidad (servicios), `KGM` kilogramo, `HUR` hora…") String unidad,
            @NotNull @Positive @Schema(example = "2", description = "Cantidad, hasta 10 decimales") BigDecimal cantidad,
            @NotNull @PositiveOrZero @Schema(example = "1000.00", description = "Precio de venta unitario **con IGV incluido** (gravados); khipu calcula el valor unitario sin IGV") BigDecimal precioUnitario,
            @NotBlank @Pattern(regexp = "10|20|30") @Schema(example = "10", description = "Afectación del IGV, catálogo 07: `10` gravado (IGV 18 %), `20` exonerado (sin IGV por ley: Apéndice I), `30` inafecto (fuera del ámbito del IGV). Las gratuitas (11–17, 21, 31–37) están en desarrollo") String tipoAfectacionIgv,
            @Valid @Schema(description = "Descuento de la línea (catálogo 53: `00` si afecta la base del IGV, `01` si no). Opcional.") DescuentoDto descuento) {}

    /** Un descuento se expresa como porcentaje **o** como monto (sobre el valor de venta sin IGV), nunca ambos. */
    public record DescuentoDto(
            @Schema(example = "10", description = "Porcentaje sobre el valor de venta sin IGV (hasta 5 decimales, menor que 100)") BigDecimal porcentaje,
            @Schema(example = "50.00", description = "Monto fijo sin IGV (hasta 2 decimales, menor que la base)") BigDecimal monto,
            @Schema(example = "true", description = "`true` (por defecto): reduce la base imponible y por tanto el IGV. `false`: reduce solo lo que se paga (descuento financiero)") Boolean afectaBaseIgv) {

        Descuento aDominio() {
            if ((porcentaje == null) == (monto == null))
                throw new DomainException("DESCUENTO_INVALIDO", "Indique porcentaje o monto del descuento, no ambos");
            boolean afecta = afectaBaseIgv == null || afectaBaseIgv;
            return porcentaje != null ? Descuento.porcentaje(porcentaje, afecta) : Descuento.monto(monto, afecta);
        }
    }

    public record FormaPagoDto(
            @NotBlank @Pattern(regexp = "contado|credito") @Schema(example = "credito", description = "`contado`: pago único al emitir. `credito`: pago diferido; exige `monto_pendiente` y al menos una cuota (RS 193-2020)") String tipo,
            @Schema(example = "1180.00", description = "Monto neto pendiente de pago; obligatorio al crédito. Formato e importes los valida el dominio con el código SUNAT (3250, 3265, 3319)") BigDecimal montoPendiente,
            @Valid @Schema(description = "Cuotas; obligatorias al crédito y deben sumar el monto pendiente") List<CuotaDto> cuotas) {

        public record CuotaDto(
                @Schema(example = "590.00", description = "Positivo, hasta 2 decimales (SUNAT 3253)") BigDecimal monto,
                @Schema(example = "2026-10-15", description = "Posterior a la fecha de emisión (SUNAT 3256, 3267)") LocalDate vencimiento) {}

        FormaPago aDominio() {
            List<FormaPago.Cuota> cs = cuotas == null ? List.of() : cuotas.stream().map(q -> new FormaPago.Cuota(q.monto(), q.vencimiento())).toList();
            return "credito".equals(tipo) ? FormaPago.credito(montoPendiente, cs) : new FormaPago(FormaPago.Tipo.CONTADO, montoPendiente, cs);
        }
    }

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), TipoAfectacionIgv.porCodigo(i.tipoAfectacionIgv()),
                        i.descuento() == null ? null : i.descuento().aDominio())).toList(),
                formaPago == null ? FormaPago.contado() : formaPago.aDominio(),
                descuentoGlobal == null ? null : descuentoGlobal.aDominio(),
                enviarAutomatico == null || enviarAutomatico);
    }
}
