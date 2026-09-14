package pe.factura.adapters.rest.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
public record CredencialesSolRequest(
        @NotBlank @Schema(example = "MODDATOS", description = "Usuario SOL secundario, no la clave SOL principal") String usuario,
        @NotBlank @Schema(example = "moddatos") String clave) {}
