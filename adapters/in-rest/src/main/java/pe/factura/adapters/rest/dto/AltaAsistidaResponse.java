package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AltaAsistidaUseCase.AltaCreada;

import java.util.UUID;

public record AltaAsistidaResponse(
        @Schema(example = "0b1f1c3e-0f1c-4b53-9a1e-2f6f6d0c7a11") UUID cuentaId,
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID tenantId,
        @Schema(example = "20123456786") String ruc,
        @Schema(example = "fk_9k2m4p1q7r3s5t8u", description = "Solo se muestra una vez, en texto plano") String apiKey,
        SerieCreada serie,
        @Schema(description = "false si el correo de invitación no salió; el alta queda hecha y el cliente puede pedir un enlace con «olvidé mi contraseña»") boolean invitacionEnviada) {

    public record SerieCreada(@Schema(example = "01") String tipo, @Schema(example = "F001") String serie) {}

    public static AltaAsistidaResponse de(AltaCreada r) {
        return new AltaAsistidaResponse(r.cuentaId(), r.tenant().id(), r.tenant().ruc(), r.apiKeyEnClaro(),
                new SerieCreada(r.tipoSerie().codigo(), r.serie()), r.invitacionEnviada());
    }
}
