package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.EquipoDtos;
import pe.factura.application.port.in.GestionarEquipoUseCase;

import java.util.List;
import java.util.UUID;

/**
 * El equipo de la cuenta (#21): sus usuarios con su rol y las invitaciones para sumar a otros. Solo con sesión de un administrador de la cuenta (portal): el filtro de JWT
 * responde `403 ROL_INSUFICIENTE` a los demás roles y una API key no tiene cuenta.
 */
@RestController
@RequestMapping("/v1/cuenta")
@Tag(name = "Equipo de la cuenta (portal)", description = """
        Usuarios de la cuenta y sus roles: `ADMIN` (todo), `EMISOR` (emite, consulta y anula comprobantes, mantiene clientes y productos; no cambia empresas, series,
        certificado, credenciales SOL, API keys, webhooks ni el equipo) y `LECTURA` (solo consulta). Solo un `ADMIN` gestiona el equipo, y la cuenta nunca se queda sin un
        `ADMIN` activo. Solo con sesión del portal: con `X-Api-Key` no hay cuenta y responde `401`.""")
public class CuentaEquipoController {
    private final GestionarEquipoUseCase equipo;
    private final String portalUrl;

    public CuentaEquipoController(GestionarEquipoUseCase equipo, @Value("${app.portal-url}") String portalUrl) {
        this.equipo = equipo;
        this.portalUrl = portalUrl;
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Usuarios de la cuenta", description = "Activos y desactivados, por fecha de alta.")
    public ApiResponse<List<EquipoDtos.Miembro>> usuariosDeLaCuenta(HttpServletRequest req) {
        UUID yo = UsuarioActual.id(req);
        return ApiResponse.ok(equipo.miembros(CuentaActual.id(req), yo).stream().map(u -> EquipoDtos.Miembro.de(u, yo)).toList());
    }

    @PutMapping("/usuarios/{id}/rol")
    @Operation(summary = "Cambiar el rol de un usuario", description = """
            Vale desde la siguiente petición del usuario, sin que vuelva a entrar. Quitarle el rol de administrador revoca las invitaciones pendientes que creó. `409 ULTIMO_ADMINISTRADOR` si le quitaría el rol al único `ADMIN` activo (también a uno
            mismo).""")
    public ApiResponse<EquipoDtos.Miembro> cambiarRolDeUsuario(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody EquipoDtos.CambiarRol body) {
        UUID yo = UsuarioActual.id(req);
        return ApiResponse.ok(EquipoDtos.Miembro.de(equipo.cambiarRol(CuentaActual.id(req), yo, id, body.rolElegido()), yo));
    }

    @PostMapping("/usuarios/{id}/desactivar")
    @Operation(summary = "Desactivar un usuario", description = """
            Ya no entra y se cierran sus sesiones abiertas. Si era administrador, se revocan las invitaciones pendientes que creó. Las API keys que haya creado
            **siguen vigentes**: son de la empresa, no del usuario; revóquelas en `DELETE /v1/empresa/api-keys/{id}` si hace falta. `422 NO_PUEDES_DESACTIVARTE` con
            el propio usuario. Se puede reactivar.""")
    public ApiResponse<EquipoDtos.Miembro> desactivarUsuario(HttpServletRequest req, @PathVariable UUID id) {
        UUID yo = UsuarioActual.id(req);
        return ApiResponse.ok(EquipoDtos.Miembro.de(equipo.desactivar(CuentaActual.id(req), yo, id), yo));
    }

    @PostMapping("/usuarios/{id}/reactivar")
    @Operation(summary = "Reactivar un usuario", description = "Vuelve a entrar con su contraseña y el rol que tenía.")
    public ApiResponse<EquipoDtos.Miembro> reactivarUsuario(HttpServletRequest req, @PathVariable UUID id) {
        UUID yo = UsuarioActual.id(req);
        return ApiResponse.ok(EquipoDtos.Miembro.de(equipo.reactivar(CuentaActual.id(req), yo, id), yo));
    }

    @GetMapping("/invitaciones")
    @Operation(summary = "Invitaciones pendientes", description = "Las que no se aceptaron ni revocaron, también las vencidas (`vencida: true`), que se pueden reenviar.")
    public ApiResponse<List<EquipoDtos.InvitacionPendiente>> invitacionesPendientes(HttpServletRequest req) {
        return ApiResponse.ok(equipo.invitaciones(CuentaActual.id(req), UsuarioActual.id(req)).stream().map(EquipoDtos.InvitacionPendiente::de).toList());
    }

    @PostMapping("/invitaciones")
    @Operation(summary = "Invitar a alguien a la cuenta", description = """
            Le manda un enlace de un solo uso (7 días) para crear su contraseña y entrar con el rol elegido. `409 YA_ES_MIEMBRO` si ya es usuario de la cuenta,
            `409 USUARIO_EXISTENTE` si el correo ya tiene un usuario en khipu (cada usuario es de una sola cuenta), `409 INVITACION_PENDIENTE` si ya hay una pendiente para
            ese correo (reenvíela). `429 DEMASIADAS_INVITACIONES` con 20 pendientes, o si en las últimas 24 horas la cuenta ya mandó 30 correos de invitación o esa
            dirección ya recibió 3 (de cualquier cuenta); invitar y reenviar cuentan, también las invitaciones que después se revocaron. La invitación vale mientras
            quien la creó siga siendo administrador activo. `correo_enviado: false` si el correo no salió: la invitación queda y se puede reenviar.""")
    public ResponseEntity<ApiResponse<EquipoDtos.Enviada>> invitarAlEquipo(HttpServletRequest req, @Valid @RequestBody EquipoDtos.Invitar body) {
        var enviada = equipo.invitar(CuentaActual.id(req), UsuarioActual.id(req), body.email(), body.rolElegido(), portalUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(EquipoDtos.Enviada.de(enviada)));
    }

    @PostMapping("/invitaciones/{id}/reenviar")
    @Operation(summary = "Reenviar una invitación", description = """
            Un enlace nuevo que vuelve a durar 7 días; el anterior deja de servir. Hasta 5 envíos por invitación: después, `429 DEMASIADOS_ENVIOS`. Cuenta para
            los topes de 24 horas de invitar (`429 DEMASIADAS_INVITACIONES`).
            `409 INVITACION_NO_PENDIENTE` si ya se aceptó o se revocó.""")
    public ApiResponse<EquipoDtos.Enviada> reenviarInvitacion(HttpServletRequest req, @PathVariable UUID id) {
        return ApiResponse.ok(EquipoDtos.Enviada.de(equipo.reenviar(CuentaActual.id(req), UsuarioActual.id(req), id, portalUrl)));
    }

    @DeleteMapping("/invitaciones/{id}")
    @Operation(summary = "Revocar una invitación", description = "El enlace deja de servir. `409 INVITACION_NO_PENDIENTE` si ya se aceptó o se revocó.")
    public ResponseEntity<Void> revocarInvitacion(HttpServletRequest req, @PathVariable UUID id) {
        equipo.revocar(CuentaActual.id(req), UsuarioActual.id(req), id);
        return ResponseEntity.noContent().build();
    }
}
