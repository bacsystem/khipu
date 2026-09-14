package pe.factura.adapters.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.factura.domain.tenant.Entorno;

public record CrearEmpresaRequest(
        @NotBlank @Pattern(regexp = "\\d{11}") String ruc,
        @NotBlank String razonSocial,
        @NotNull Entorno entorno) {}
