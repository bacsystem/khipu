package pe.factura.application.port.in;

import pe.factura.domain.plataforma.Administrador;

/** Alta de una cuenta de administrador. Sin registro público: solo la usa quien ya tiene X-Platform-Key. */
public interface CrearAdministradorUseCase {
    Administrador crear(String email, String password);
}
