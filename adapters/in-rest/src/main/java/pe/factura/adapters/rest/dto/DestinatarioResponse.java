package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.SoporteDeAccesoUseCase.Destinatario;

import java.util.UUID;

/** A quién se le mandó el correo de acceso (#183). A propósito, sin el enlace ni su token: solo el usuario puede usarlo. */
public record DestinatarioResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID usuarioId,
        @Schema(example = "ana@negocio.pe", description = "El correo al que se mandó") String correo) {
    public static DestinatarioResponse de(Destinatario d) {
        return new DestinatarioResponse(d.usuarioId(), d.correo());
    }
}
