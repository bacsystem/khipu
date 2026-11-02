package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import pe.factura.application.port.in.EnviarAlClienteUseCase.Preferencia;

/** La preferencia de envío automático del comprobante aceptado a su cliente (#325). */
public final class EnvioAlClienteDtos {
    private EnvioAlClienteDtos() {}

    @Schema(name = "PreferenciaDeEnvioAlClienteRequest")
    public record PreferenciaRequest(
            @NotNull @Schema(example = "true", description = "`true` envía el comprobante a su cliente cuando SUNAT lo acepta (si se emitió con `cliente.email`); `false` deja de hacerlo") Boolean enviarAutomaticamente) {}

    @Schema(name = "PreferenciaDeEnvioAlCliente")
    public record PreferenciaResponse(
            @Schema(example = "true", description = "Si la empresa envía automáticamente el comprobante aceptado a su cliente") boolean enviarAutomaticamente,
            @Schema(example = "true", description = "Si el servidor de khipu tiene correo configurado. Sin él no se puede activar el envío (`503 CORREO_NO_CONFIGURADO`) y lo que quedó pendiente se omite") boolean correoDisponible) {
        public static PreferenciaResponse de(Preferencia p) { return new PreferenciaResponse(p.automatico(), p.correoDisponible()); }
    }
}
