package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Rol;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Emite y verifica tokens de acceso firmados (JWT). */
public interface TokenEmisor {
    /**
     * Lo que dice un token de acceso. {@code soporte} es nulo en una sesión normal; en una sesión de soporte (#184: un administrador mirando el portal como un
     * cliente) dice qué administrador la abrió y hasta cuándo vale, y esa expiración manda sobre la de las sesiones normales. Una sesión de soporte no tiene
     * refresh y el filtro de JWT la deja solo leer.
     */
    record Claims(UUID usuarioId, UUID cuentaId, Rol rol, Soporte soporte) {
        public Claims(UUID usuarioId, UUID cuentaId, Rol rol) { this(usuarioId, cuentaId, rol, null); }
        public boolean esSoporte() { return soporte != null; }
    }

    record Soporte(UUID administradorId, Instant expiraEn) {}

    String emitir(Claims claims);

    /** Vacío si el token es inválido o expiró, o si trae un claim de soporte que no se entiende: ante la duda no se trata como una sesión normal. */
    Optional<Claims> verificar(String token);
}
