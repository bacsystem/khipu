package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.factura.domain.tenant.Entorno;

/** Alta asistida (#188). Sin contraseña a propósito: la elige el cliente al aceptar la invitación que recibe por correo. */
public record AltaAsistidaRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Comercial Andina") String nombre,
        @NotBlank @Size(max = 254) @Schema(example = "ana@andina.pe", description = "Correo del primer usuario de la cuenta; recibe la invitación") String email,
        @Schema(example = "987654321", description = "Celular de contacto (9 dígitos, empieza con 9); opcional") String telefono,
        @NotNull @Valid Empresa empresa,
        @NotNull @Valid Serie serie) {

    public record Empresa(
            @NotBlank @Pattern(regexp = "\\d{11}") @Schema(example = "20123456786") String ruc,
            @NotBlank @Schema(example = "Comercial Andina SAC") String razonSocial,
            @Schema(example = "BETA", description = "Por defecto BETA") Entorno entorno) {}

    public record Serie(
            @NotBlank @Pattern(regexp = "01|03|07|08") @Schema(example = "01", description = "01=factura, 03=boleta, 07=nota de crédito, 08=nota de débito") String tipo,
            @NotBlank @Schema(example = "F001") String serie) {}
}
