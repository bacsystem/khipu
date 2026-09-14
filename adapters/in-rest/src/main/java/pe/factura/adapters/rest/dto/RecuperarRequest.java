package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RecuperarRequest(@NotBlank @Schema(example = "facturacion@comercialandina.pe") String email) {}
