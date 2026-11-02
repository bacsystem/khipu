package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Una invitación a sumarse a una cuenta con un rol (#21). El enlace lleva un token de un solo uso; aquí solo está su hash. Sirve mientras esté {@link Estado#PENDIENTE} y sin
 * vencer: aceptarla, revocarla o reenviarla (que cambia el token) deja inservible el enlace anterior.
 */
public record Invitacion(UUID id, UUID cuentaId, String email, Rol rol, String tokenHash, Instant expiraEn, Estado estado, UUID invitadoPor, int envios, Instant creadaEn) {
    /** Cuántas veces se puede mandar el mismo enlace, contando el primero: sin tope, reenviar serviría para llenar el buzón de alguien desde nuestro dominio. */
    public static final int MAX_ENVIOS = 5;

    public enum Estado { PENDIENTE, ACEPTADA, REVOCADA }

    public Invitacion {
        email = Usuario.normalizarEmail(email);
        if (rol == null) throw new DomainException("ROL_REQUERIDO", "Rol requerido");
        if (tokenHash == null || tokenHash.isBlank()) throw new IllegalArgumentException("Sin hash de token");
        if (estado == null) throw new IllegalArgumentException("Sin estado");
    }

    public static Invitacion nueva(UUID cuentaId, String email, Rol rol, String tokenHash, Instant ahora, Duration vida, UUID invitadoPor) {
        return new Invitacion(UUID.randomUUID(), cuentaId, email, rol, tokenHash, ahora.plus(vida), Estado.PENDIENTE, invitadoPor, 1, ahora);
    }

    public boolean vencida(Instant ahora) { return !expiraEn.isAfter(ahora); }

    public boolean pendiente() { return estado == Estado.PENDIENTE; }

    /** Se puede aceptar: pendiente y sin vencer. */
    public boolean aceptable(Instant ahora) { return pendiente() && !vencida(ahora); }

    /** Un enlace nuevo, que vuelve a durar {@code vida} desde ahora; el anterior deja de servir. Vale también para una vencida, que es justo cuando hace falta. */
    public Invitacion reenviada(String nuevoHash, Instant ahora, Duration vida) {
        exigirPendiente();
        if (envios >= MAX_ENVIOS)
            throw new DomainException("DEMASIADOS_ENVIOS", "Ya enviamos esta invitación " + MAX_ENVIOS + " veces. Revócala e invita de nuevo si hace falta");
        return new Invitacion(id, cuentaId, email, rol, nuevoHash, ahora.plus(vida), estado, invitadoPor, envios + 1, creadaEn);
    }

    public Invitacion revocada() {
        exigirPendiente();
        return new Invitacion(id, cuentaId, email, rol, tokenHash, expiraEn, Estado.REVOCADA, invitadoPor, envios, creadaEn);
    }

    private void exigirPendiente() {
        if (!pendiente()) throw new DomainException("INVITACION_NO_PENDIENTE", estado == Estado.ACEPTADA ? "La invitación ya fue aceptada" : "La invitación fue revocada");
    }
}
