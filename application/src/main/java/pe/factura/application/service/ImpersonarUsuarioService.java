package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.DetalleDeSoporte;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ImpersonarUsuarioService implements ImpersonarUsuarioUseCase {
    private final UsuarioRepository usuarios;
    private final TokenEmisor tokens;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public Impersonacion impersonar(ActorAdmin actor, UUID cuentaId, UUID usuarioId) {
        // Impersonar es de una persona: la clave de plataforma no identifica a nadie y la bitácora no podría decir quién fue.
        if (actor.tipo() != ActorAdmin.Tipo.ADMINISTRADOR)
            throw new DomainException("REQUIERE_ADMINISTRADOR", "Impersonar a un usuario requiere la sesión de un administrador, no la clave de plataforma");
        // Dentro de la cuenta de la ruta: un usuario de otra cuenta no existe para esta pantalla.
        Usuario u = cuentaId == null || usuarioId == null ? null : usuarios.buscar(usuarioId).filter(x -> x.cuentaId().equals(cuentaId)).orElse(null);
        if (u == null) throw new DomainException("NO_ENCONTRADO", "El usuario no existe en esta cuenta");
        if (!u.activo()) throw new DomainException("USUARIO_INACTIVO", "El usuario " + u.email() + " está desactivado");
        Instant ahora = clock.instant();
        Instant expira = ahora.plus(DURACION);
        // Firmar no tiene efectos: se firma primero y la bitácora va después, así que un token nunca sale sin su registro (si la bitácora falla, la excepción
        // se lleva el token) y un registro nunca queda sin su token.
        String token = tokens.emitir(new TokenEmisor.Claims(u.id(), u.cuentaId(), u.rol(), new TokenEmisor.Soporte(actor.administradorId(), expira)));
        uow.ejecutar(() -> auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.IMPERSONAR_USUARIO, cuentaId, null,
                new DetalleDeSoporte(u.email(), DURACION.toSeconds()).texto(), ahora)));
        return new Impersonacion(token, expira, u);
    }
}
