package pe.factura.adapters.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import pe.factura.domain.tenant.Entorno;
public record CrearTenantRequest(
        @NotBlank @Pattern(regexp = "\\d{11}") @Schema(example = "20123456786") String ruc,
        @NotBlank @Schema(example = "Comercial Andina SAC") String razonSocial,
        @NotNull @Schema(example = "BETA") Entorno entorno) {}
