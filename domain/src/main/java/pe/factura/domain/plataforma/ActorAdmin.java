package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.util.UUID;

/**
 * Quién ejecuta una acción administrativa: un administrador con sesión (JWT) o, para herramientas internas,
 * la clave de plataforma ({@code X-Platform-Key}), que no identifica a ninguna persona. La IP es obligatoria:
 * una bitácora sin ella no permite explicar de dónde vino la acción.
 */
public record ActorAdmin(Tipo tipo, UUID administradorId, String ip) {
    public enum Tipo { ADMINISTRADOR, CLAVE_PLATAFORMA }

    public ActorAdmin {
        if (tipo == null) throw new DomainException("ACTOR_INVALIDO", "Tipo de actor requerido");
        if (ip == null || ip.isBlank()) throw new DomainException("ACTOR_INVALIDO", "IP de origen requerida");
        if (tipo == Tipo.ADMINISTRADOR && administradorId == null)
            throw new DomainException("ACTOR_INVALIDO", "Un administrador debe tener id");
        if (tipo == Tipo.CLAVE_PLATAFORMA && administradorId != null)
            throw new DomainException("ACTOR_INVALIDO", "La clave de plataforma no identifica a un administrador");
        ip = ip.trim();
    }

    public static ActorAdmin administrador(UUID administradorId, String ip) { return new ActorAdmin(Tipo.ADMINISTRADOR, administradorId, ip); }

    public static ActorAdmin clavePlataforma(String ip) { return new ActorAdmin(Tipo.CLAVE_PLATAFORMA, null, ip); }
}
