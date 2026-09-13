package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.*;
public record SerieRequest(@NotBlank @Pattern(regexp = "01|03|07|08") String tipo, @NotBlank String serie, @PositiveOrZero Long correlativoInicial) {}
