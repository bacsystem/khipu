package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCodigoRequest(
        @NotBlank @Size(max = 2000) @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String desafio,
        @NotBlank @Size(max = 64) @Schema(description = "Los 6 dígitos de la app, o un código de recuperación (`XXXXX-XXXXX`).", example = "123456") String codigo) {}
