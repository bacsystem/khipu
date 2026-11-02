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
    /** Cuánto vive un token de sesión, en segundos: el portal fija con esto la vida de su cookie. */
    long vidaSesionSegundos();

    /**
     * Token del paso intermedio del login (#177): la contraseña ya se comprobó, falta el segundo factor. Es de otro tipo que el de
     * sesión, así que {@link #verificar} lo rechaza: con él no se opera el backoffice, solo se completa el login.
     */
    String emitirDesafio(UUID administradorId);
    /** Vacío si el token no es de desafío, expiró o es inválido. */
    Optional<UUID> verificarDesafio(String token);
}
