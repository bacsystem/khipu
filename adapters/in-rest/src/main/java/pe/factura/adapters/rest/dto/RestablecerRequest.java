package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RestablecerRequest(
        @NotBlank @Schema(example = "3f7a2c9e-1b4d-4e8a-9c2f-6d5e4a3b2c1d") String token,
        @NotBlank @Schema(example = "Cambiar123") String password) {}
