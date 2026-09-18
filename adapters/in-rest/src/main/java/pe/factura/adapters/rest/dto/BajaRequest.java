package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BajaRequest(
        @NotBlank @Size(min = 3, max = 100) @Schema(example = "Error en el RUC del cliente", description = "Motivo de la baja (3–100 caracteres, sin saltos de línea; reglas 2315, 4203). Va en el XML y lo ve SUNAT") String motivo) {}
