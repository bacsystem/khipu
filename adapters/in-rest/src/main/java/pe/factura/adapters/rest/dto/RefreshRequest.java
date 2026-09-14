package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String refresh) {}
