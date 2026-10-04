package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminDesafioRequest(@NotBlank @Size(max = 2000) @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String desafio) {}
