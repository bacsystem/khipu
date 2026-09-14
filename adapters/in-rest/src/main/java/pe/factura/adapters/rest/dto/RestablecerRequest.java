package pe.factura.adapters.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RestablecerRequest(@NotBlank String token, @NotBlank String password) {}
