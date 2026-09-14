package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Rol;
import java.util.Optional;
import java.util.UUID;

/** Emite y verifica tokens de acceso firmados (JWT). */
public interface TokenEmisor {
    record Claims(UUID usuarioId, UUID cuentaId, Rol rol) {}
    String emitir(Claims claims);
    /** Vacío si el token es inválido o expiró. */
    Optional<Claims> verificar(String token);
}
