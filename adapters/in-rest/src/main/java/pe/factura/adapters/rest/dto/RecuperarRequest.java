package pe.factura.adapters.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RecuperarRequest(@NotBlank String email) {}
