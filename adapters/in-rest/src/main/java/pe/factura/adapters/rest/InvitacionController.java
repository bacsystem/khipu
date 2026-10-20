package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.EquipoDtos;
import pe.factura.application.port.in.AceptarInvitacionUseCase;

/** Lo que hace el invitado con el enlace del correo (#21). Públicas: todavía no tiene usuario. El token va en el cuerpo, no en la URL, para que no quede en los registros. */
@RestController
@RequestMapping("/v1/auth/invitacion")
@Tag(name = "Autenticación (portal)")
@RequiredArgsConstructor
public class InvitacionController {
    private final AceptarInvitacionUseCase invitaciones;

    @PostMapping
    @Operation(summary = "Ver una invitación", description = """
            A qué cuenta y con qué rol invitaron al dueño del enlace. `422 TOKEN_INVALIDO` si no existe, venció, se revocó, se reenvió (vale el último enlace) o ya se usó.""")
    public ApiResponse<EquipoDtos.Recibida> consultar(@Valid @RequestBody EquipoDtos.Token body) {
        return ApiResponse.ok(EquipoDtos.Recibida.de(invitaciones.consultar(body.token())));
    }

    @PostMapping("/aceptar")
    @Operation(summary = "Aceptar una invitación", description = """
            Crea el usuario en la cuenta con el rol de la invitación y el correo ya verificado (el enlace llegó ahí), con la contraseña elegida; después entra con
            `POST /v1/auth/login`. `422 TOKEN_INVALIDO` como al verla, `422 PASSWORD_DEBIL` (no consume el enlace), `409 USUARIO_EXISTENTE` si el correo ya tiene un usuario.""")
    public ResponseEntity<Void> aceptar(@Valid @RequestBody EquipoDtos.Aceptar body) {
        invitaciones.aceptar(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
