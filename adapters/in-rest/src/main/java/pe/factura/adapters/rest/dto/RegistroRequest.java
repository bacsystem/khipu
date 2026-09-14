package pe.factura.adapters.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 254) String email,
        @NotBlank String password) {}
