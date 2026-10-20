package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Invitacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Invitaciones a sumarse a una cuenta (#21). Del token solo se guarda el hash. */
public interface InvitacionRepository {
    void crear(Invitacion i);

    /**
     * Guarda el reenvío o la revocación de una invitación, solo si en la base sigue pendiente, y devuelve si lo hizo: un reenvío no revive una invitación que se aceptó en
     * el medio.
     */
    boolean actualizarPendiente(Invitacion i);

    Optional<Invitacion> buscar(UUID id);

    Optional<Invitacion> buscarPorTokenHash(String tokenHash);

    /** Las pendientes de la cuenta (vencidas o no), las más recientes primero. */
    List<Invitacion> pendientes(UUID cuentaId);

    /**
     * La pasa a aceptada si seguía pendiente y sin vencer a {@code ahora}, con ese mismo token, y devuelve si lo hizo: dos clics a la vez en el mismo enlace, o uno que llega
     * mientras la revocan o la reenvían, no la aceptan dos veces ni con un enlace que ya no vale.
     */
    boolean aceptar(UUID id, String tokenHash, Instant ahora);
}
