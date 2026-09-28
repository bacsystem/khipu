package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.AdministradorTokenEmisor;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

@RequiredArgsConstructor
public class AutenticarAdministradorService implements AutenticarAdministradorUseCase {
    private final AdministradorRepository administradores;
    private final PasswordHasher hasher;
    private final AdministradorTokenEmisor tokens;

    @Override
    public Sesion login(String email, String password) {
        Administrador a = administradores.buscarPorEmail(email == null ? "" : email.trim().toLowerCase())
                .filter(Administrador::activo)
                .filter(x -> hasher.coincide(password == null ? "" : password, x.passwordHash()))
                .orElseThrow(() -> new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        String access = tokens.emitir(new AdministradorTokenEmisor.Claims(a.id(), a.email()));
        return new Sesion(access, a);
    }

    @Override
    public Administrador me(UUID administradorId) {
        return administradores.buscar(administradorId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Administrador no encontrado"));
    }
}
