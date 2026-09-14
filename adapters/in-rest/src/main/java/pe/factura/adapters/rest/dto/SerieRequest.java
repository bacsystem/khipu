package pe.factura.adapters.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
public record SerieRequest(
        @NotBlank @Pattern(regexp = "01|03|07|08") @Schema(example = "01", description = "01=factura, 03=boleta, 07=nota de crédito, 08=nota de débito") String tipo,
        @NotBlank @Schema(example = "F001") String serie,
        @PositiveOrZero @Schema(example = "0") Long correlativoInicial) {}
