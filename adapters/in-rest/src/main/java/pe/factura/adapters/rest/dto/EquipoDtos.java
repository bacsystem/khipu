package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.factura.application.port.in.AceptarInvitacionUseCase;
import pe.factura.application.port.in.GestionarEquipoUseCase;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.util.UUID;

/** Pedidos y respuestas del equipo de una cuenta y sus invitaciones (#21). */
public final class EquipoDtos {
    private EquipoDtos() {}

    static final String ROLES = "ADMIN|EMISOR|LECTURA";
    static final String DESCRIPCION_ROL = "`ADMIN` todo; `EMISOR` emite, consulta y anula comprobantes y mantiene clientes y productos, pero no cambia la configuración ni el equipo;"
            + " `LECTURA` solo consulta.";

    @Schema(name = "MiembroDelEquipo")
    public record Miembro(
            @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
            @Schema(example = "caja@ferreteriatorres.pe") String email,
            @Schema(example = "EMISOR", allowableValues = {"ADMIN", "EMISOR", "LECTURA"}) String rol,
            @Schema(description = "`false`: desactivado, no puede entrar.", example = "true") boolean activo,
            @Schema(example = "true") boolean correoVerificado,
            @Schema(description = "`true` en el usuario de la sesión.", example = "false") boolean esUsted) {
        public static Miembro de(Usuario u, UUID usuarioDeLaSesion) {
            return new Miembro(u.id(), u.email(), u.rol().name(), u.activo(), u.correoVerificado(), u.id().equals(usuarioDeLaSesion));
        }
    }

    @Schema(name = "InvitacionAlEquipo")
    public record InvitacionPendiente(
            @Schema(example = "5e0b7c1a-2d3f-4a5b-8c6d-7e8f9a0b1c2d") UUID id,
            @Schema(example = "contador@estudio.pe") String email,
            @Schema(example = "LECTURA", allowableValues = {"ADMIN", "EMISOR", "LECTURA"}) String rol,
            @Schema(example = "2026-10-27T12:00:00Z") Instant expiraEn,
            @Schema(description = "`true` si el enlace ya venció: se puede reenviar.", example = "false") boolean vencida,
            @Schema(description = "Cuántas veces se mandó el enlace, contando el primero (hasta 5).", example = "1") int envios,
            @Schema(example = "2026-10-20T12:00:00Z") Instant creadaEn) {
        public static InvitacionPendiente de(Invitacion i) {
            Instant ahora = Instant.now();
            return new InvitacionPendiente(i.id(), i.email(), i.rol().name(), i.expiraEn(), i.vencida(ahora), i.envios(), i.creadaEn());
        }
    }

    @Schema(name = "InvitacionEnviada")
    public record Enviada(
            InvitacionPendiente invitacion,
            @Schema(description = "`false` si el correo no salió (servidor de correo caído o sin configurar): la invitación queda pendiente y se puede reenviar.", example = "true")
            boolean correoEnviado) {
        public static Enviada de(GestionarEquipoUseCase.Enviada e) { return new Enviada(InvitacionPendiente.de(e.invitacion()), e.correoEnviado()); }
    }

    @Schema(name = "InvitarAlEquipoRequest")
    public record Invitar(
            @NotBlank @Email @Size(max = 254) @Schema(example = "contador@estudio.pe") String email,
            @NotBlank @Pattern(regexp = ROLES, message = "debe ser ADMIN, EMISOR o LECTURA") @Schema(example = "LECTURA", allowableValues = {"ADMIN", "EMISOR", "LECTURA"}, description = DESCRIPCION_ROL)
            String rol) {
        public Rol rolElegido() { return Rol.valueOf(rol); }
    }

    @Schema(name = "CambiarRolRequest")
    public record CambiarRol(
            @NotBlank @Pattern(regexp = ROLES, message = "debe ser ADMIN, EMISOR o LECTURA") @Schema(example = "EMISOR", allowableValues = {"ADMIN", "EMISOR", "LECTURA"}, description = DESCRIPCION_ROL)
            String rol) {
        public Rol rolElegido() { return Rol.valueOf(rol); }
    }

    @Schema(name = "InvitacionTokenRequest")
    public record Token(@NotBlank @Size(max = 200) @Schema(example = "q8Zf3kV1x...") String token) {}

    @Schema(name = "AceptarInvitacionRequest")
    public record Aceptar(
            @NotBlank @Size(max = 200) @Schema(example = "q8Zf3kV1x...") String token,
            @NotBlank @Size(max = 200) @Schema(example = "Segura123", description = "Al menos 8 caracteres, una letra y un dígito.") String password) {}

    @Schema(name = "InvitacionRecibida")
    public record Recibida(
            @Schema(example = "contador@estudio.pe") String email,
            @Schema(description = "El nombre de la cuenta que invita.", example = "Ferretería Torres") String cuenta,
            @Schema(example = "LECTURA", allowableValues = {"ADMIN", "EMISOR", "LECTURA"}) String rol,
            @Schema(example = "2026-10-27T12:00:00Z") Instant expiraEn) {
        public static Recibida de(AceptarInvitacionUseCase.Detalle d) { return new Recibida(d.email(), d.cuenta(), d.rol().name(), d.expiraEn()); }
    }
}
