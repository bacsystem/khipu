package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.*;
import pe.factura.domain.tenant.Entorno;
public record CrearTenantRequest(@NotBlank @Pattern(regexp = "\\d{11}") String ruc, @NotBlank String razonSocial, @NotNull Entorno entorno) {}
