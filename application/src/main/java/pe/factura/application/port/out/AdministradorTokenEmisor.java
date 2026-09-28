package pe.factura.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Emite y verifica el JWT de sesión del administrador de la plataforma. Deliberadamente un puerto
 * distinto de {@link TokenEmisor} (cliente): sus claims no llevan cuenta ni rol de tenant, así que un
 * token de cliente nunca puede decodificarse como uno de administrador, ni viceversa.
 */
public interface AdministradorTokenEmisor {
    record Claims(UUID administradorId, String email) {}
    String emitir(Claims claims);
    /** Vacío si el token es inválido, expiró, o es un token de cliente (otro tipo de claims). */
    Optional<Claims> verificar(String token);
}
