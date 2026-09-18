package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CorreoRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "compras@cliente.pe", description = "Correo del adquirente al que se envía el comprobante") String email,
        @Size(max = 1000) @Schema(example = "Gracias por su compra.", description = "Texto opcional que encabeza el correo") String mensaje) {
    /** Jackson pasa por aquí antes de la validación: el correo se normaliza (espacios, mayúsculas) y un mensaje en blanco es ausencia. */
    public CorreoRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        mensaje = mensaje == null || mensaje.isBlank() ? null : mensaje.strip();
    }
}
