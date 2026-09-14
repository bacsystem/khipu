package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Comercial Andina SAC") String nombre,
        @NotBlank @Size(max = 254) @Schema(example = "facturacion@comercialandina.pe") String email,
        @NotBlank @Schema(example = "Cambiar123", description = "Mínimo 8 caracteres, con una letra y un número") String password) {}
