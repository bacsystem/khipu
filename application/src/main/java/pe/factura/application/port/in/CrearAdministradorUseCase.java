package pe.factura.application.port.in;

import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.Administrador;

/**
 * Alta de una cuenta de administrador. Sin registro público: solo la usa quien ya tiene X-Platform-Key.
 * Queda en la bitácora de auditoría a nombre de {@code actor}, en la misma transacción que el alta.
 */
public interface CrearAdministradorUseCase {
    Administrador crear(ActorAdmin actor, String email, String password);
}
