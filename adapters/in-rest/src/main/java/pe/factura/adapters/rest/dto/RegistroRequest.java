package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Comercial Andina SAC") String nombre,
        @NotBlank @Size(max = 254) @Schema(example = "facturacion@comercialandina.pe") String email,
        @NotBlank @Schema(example = "Cambiar123", description = "Mínimo 8 caracteres, con una letra y un número") String password,
        // El formato (9 dígitos, empieza con 9; admite +51/51 y espacios o guiones) lo valida y normaliza el dominio
        // (Cuenta.normalizarTelefono, 422 TELEFONO_INVALIDO) para no duplicar la regla en dos capas que puedan divergir.
        @NotBlank @Schema(example = "987654321", description = "Celular de contacto en Perú (9 dígitos, empieza con 9); admite +51/51, espacios y guiones") String telefono) {}
