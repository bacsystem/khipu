package pe.factura.adapters.rest.dto;
import jakarta.validation.constraints.NotBlank;
public record CredencialesSolRequest(@NotBlank String usuario, @NotBlank String clave) {}
