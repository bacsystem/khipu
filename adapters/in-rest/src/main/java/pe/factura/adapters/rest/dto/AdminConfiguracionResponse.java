package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Configuracion;

import java.util.Base64;

public record AdminConfiguracionResponse(
        @Schema(description = "El secreto en Base32, para tipearlo si la app no puede leer el QR.", example = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP") String secreto,
        @Schema(example = "otpauth://totp/khipu:ana%40khipu.pe?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=khipu&algorithm=SHA1&digits=6&period=30") String uri,
        @Schema(description = "PNG del QR de `uri`, en Base64.") String qrPng) {
    public static AdminConfiguracionResponse de(Configuracion c) {
        return new AdminConfiguracionResponse(c.secreto(), c.uri(), Base64.getEncoder().encodeToString(c.qrPng()));
    }
}
