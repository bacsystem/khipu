package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank @Schema(example = "ana@khipu.pe") String email,
        @NotBlank @Schema(example = "Cambiar123") String password) {}
