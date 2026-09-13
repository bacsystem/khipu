package pe.factura.adapters.rest.dto;

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
        @NotBlank @Pattern(regexp = "F[A-Z0-9]{3}", message = "serie de factura inválida") String serie,
        @Positive Long correlativo,
        @NotNull LocalDate fechaEmision,
        @Pattern(regexp = "\\d{4}") String tipoOperacion,
        @NotBlank @Pattern(regexp = "PEN|USD|EUR") String moneda,
        @NotNull @Valid ClienteDto cliente,
        @NotEmpty @Valid List<ItemDto> items,
        Boolean enviarAutomatico) {

    public record ClienteDto(@NotBlank String tipoDoc, @NotBlank String numDoc, @NotBlank String razonSocial, String direccion) {}

    public record ItemDto(String codigo, @NotBlank String descripcion, @NotBlank String unidad,
                          @NotNull @Positive BigDecimal cantidad, @NotNull @PositiveOrZero BigDecimal precioUnitario,
                          @NotBlank @Pattern(regexp = "10|20|30") String tipoAfectacionIgv) {}

    public EmitirFacturaCommand aComando() {
        return new EmitirFacturaCommand(serie, correlativo, fechaEmision, moneda, tipoOperacion,
                new Receptor(cliente.tipoDoc(), cliente.numDoc(), cliente.razonSocial(), cliente.direccion()),
                items.stream().map(i -> new Item(i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), TipoAfectacionIgv.porCodigo(i.tipoAfectacionIgv()))).toList(),
                enviarAutomatico == null || enviarAutomatico);
    }
}
