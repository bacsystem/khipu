package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.factura.domain.tenant.Entorno;

public record CrearEmpresaRequest(
        @NotBlank @Pattern(regexp = "\\d{11}") @Schema(example = "20123456786") String ruc,
        @NotBlank @Size(min = 3, max = 1500, message = "la razón social tiene de 3 a 1500 caracteres (SUNAT 4338)")
        @Schema(example = "Comercial Andina SAC", description = "Hasta 1500 caracteres, sin saltos de línea ni tabuladores (regla 4338)") String razonSocial,
        @NotNull @Schema(example = "BETA") Entorno entorno) {}
