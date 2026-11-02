package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificarRequest(@NotBlank @Size(max = 200) @Schema(example = "q8Zf3kV1x...") String token) {}
