package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BajaRequest(
        @NotBlank @Size(min = 3, max = 100) @Schema(example = "Error en el RUC del cliente", description = "Motivo de la baja (3–100 caracteres, sin saltos de línea; reglas 2315, 4203). En una factura o nota de factura va en el XML de la comunicación de baja y lo ve SUNAT; en una boleta o nota de boleta queda solo en khipu, porque el resumen diario no lo lleva") String motivo) {}
