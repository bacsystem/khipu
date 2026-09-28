package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CrearAdministradorUseCase;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

@RequiredArgsConstructor
public class CrearAdministradorService implements CrearAdministradorUseCase {
    private final AdministradorRepository administradores;
    private final PasswordHasher hasher;

    @Override
    public Administrador crear(String email, String password) {
        Administrador.validarPassword(password);
        Administrador a = new Administrador(UUID.randomUUID(), email, hasher.hash(password), true);
        if (administradores.buscarPorEmail(a.email()).isPresent())
            throw new DomainException("DUPLICADO", "Ya existe un administrador con ese correo");
        administradores.guardar(a);
        return a;
    }
}
