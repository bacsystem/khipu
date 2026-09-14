package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.factura.application.port.in.EmitirFacturaCommand;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaRequest(
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") @Schema(example = "F001") String serie,
        @Positive @Schema(example = "125") Long correlativo,
        @NotNull @Schema(example = "2026-09-14") LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") @Schema(example = "0101", description = "Código de tipo de operación SUNAT") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") @Schema(example = "PEN") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        @Schema(example = "true", description = "Si es false, queda RECIBIDO/FIRMADO sin enviarse a SUNAT") Boolean enviarAutomatico) {

    public record ClienteDto(
            @NotBlank @Schema(example = "6", description = "Catálogo 06 SUNAT: 1=DNI, 6=RUC") String tipoDoc,
            @NotBlank @Schema(example = "20123456789") String numDoc,
            @NotBlank @Schema(example = "Comercial Andina SAC") String razonSocial,
            @Schema(example = "Av. Javier Prado Este 123, San Isidro") String direccion) {}

    public record ItemDto(
            @Schema(example = "SKU-001") String codigo,
            @NotBlank @Schema(example = "Servicio de consultoría") String descripcion,
            @NotBlank @Schema(example = "NIU", description = "Catálogo 03 SUNAT (unidad de medida), NIU = unidad") String unidad,
            @NotNull @Positive @Schema(example = "2") BigDecimal cantidad,
            @NotNull @PositiveOrZero @Schema(example = "1000.00") BigDecimal precioUnitario,
            @NotBlank @Pattern(regexp = "10|20|30") @Schema(example = "10", description = "10=gravado, 20=exonerado, 30=inafecto") String tipoAfectacionIgv) {}

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), TipoAfectacionIgv.porCodigo(i.tipoAfectacionIgv()))).toList(),
                enviarAutomatico == null || enviarAutomatico);
    }
}
