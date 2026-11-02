package pe.factura.application.port.in;

import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.util.List;
import java.util.UUID;

/**
 * El equipo de una cuenta (#21): sus usuarios y las invitaciones pendientes. Todo lo hace un administrador activo de la cuenta ({@code actorId}); el servicio lo vuelve a
 * comprobar aunque el filtro de la API ya lo exige por ruta.
 */
public interface GestionarEquipoUseCase {
    /** La invitación guardada y si el correo salió de verdad: sin SMTP, o con el SMTP caído, queda pendiente y se puede reenviar. */
    record Enviada(Invitacion invitacion, boolean correoEnviado) {}

    List<Usuario> miembros(UUID cuentaId, UUID actorId);

    Usuario cambiarRol(UUID cuentaId, UUID actorId, UUID usuarioId, Rol rol);

    /** También cierra sus sesiones: no vuelve a entrar ni con la que tenía abierta. */
    Usuario desactivar(UUID cuentaId, UUID actorId, UUID usuarioId);

    Usuario reactivar(UUID cuentaId, UUID actorId, UUID usuarioId);

    List<Invitacion> invitaciones(UUID cuentaId, UUID actorId);

    /** {@code urlPortal}: la base del enlace que se manda por correo. */
    Enviada invitar(UUID cuentaId, UUID actorId, String email, Rol rol, String urlPortal);

    /** Un enlace nuevo, que vuelve a durar la vida entera; el anterior deja de servir. */
    Enviada reenviar(UUID cuentaId, UUID actorId, UUID invitacionId, String urlPortal);

    void revocar(UUID cuentaId, UUID actorId, UUID invitacionId);
}
