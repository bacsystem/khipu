package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CrearAdministradorUseCase;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.PasswordHasher;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.Administrador;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CrearAdministradorService implements CrearAdministradorUseCase {
    private final AdministradorRepository administradores;
    private final PasswordHasher hasher;
    private final UnitOfWork uow;
    private final AuditoriaAdminRepository auditoria;
    private final Clock clock;

    @Override
    public Administrador crear(ActorAdmin actor, String email, String password) {
        Administrador.validarPassword(password);
        Administrador a = new Administrador(UUID.randomUUID(), email, hasher.hash(password), true);
        if (administradores.buscarPorEmail(a.email()).isPresent())
            throw new DomainException("DUPLICADO", "Ya existe un administrador con ese correo");
        uow.ejecutar(() -> {
            administradores.guardar(a);
            // En la misma transacción: si la bitácora falla, el alta tampoco queda. El detalle nunca lleva la contraseña ni su hash.
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.CREAR_ADMINISTRADOR, null, null, "email=" + a.email(), Instant.now(clock)));
            // En la misma transacción: si la bitácora falla, el alta tampoco queda. El detalle nunca lleva la contraseña ni su hash.
        });
        return a;
    }
}
