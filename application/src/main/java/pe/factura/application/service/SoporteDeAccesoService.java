package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.SoporteDeAccesoUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.SesionRepository;
import pe.factura.application.port.out.SesionRepository.TokenRecuperacion;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.application.port.out.VerificacionCorreoRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class SoporteDeAccesoService implements SoporteDeAccesoUseCase {
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final VerificacionCorreoRepository verificaciones;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final AuditoriaAdminRepository auditoria;
    private final Clock clock;

    @Override public Destinatario enviarRestablecimiento(ActorAdmin actor, UUID cuentaId, UUID usuarioId, String urlBase) {
        Usuario u = usuarioActivoDeLaCuenta(cuentaId, usuarioId);
        exigirCorreoQueEntregue();
        String token = TokenOpaco.generar();
        Instant ahora = clock.instant();
        // El enlace y la bitácora van en la misma transacción: si la bitácora falla, no queda un enlace que nadie sabe quién pidió.
        uow.ejecutar(() -> {
            sesiones.crearRecuperacion(new TokenRecuperacion(TokenOpaco.hash(token), u.id(), ahora.plus(AutenticarUsuarioService.VIDA_RECUPERACION), false));
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.ENVIAR_RESTABLECIMIENTO, cuentaId, null, "usuario=" + u.email(), ahora));
        });
        enviar(u, CorreosDeAcceso.ASUNTO_RECUPERACION, CorreosDeAcceso.cuerpoRecuperacion(urlBase, token));
        return new Destinatario(u.id(), u.email());
    }

    @Override public Destinatario reenviarVerificacion(ActorAdmin actor, UUID cuentaId, UUID usuarioId, String urlBase) {
        Usuario u = usuarioActivoDeLaCuenta(cuentaId, usuarioId);
        if (u.correoVerificado()) throw new DomainException("CORREO_YA_VERIFICADO", "El correo de " + u.email() + " ya está verificado");
        exigirCorreoQueEntregue();
        String token = TokenOpaco.generar();
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            verificaciones.crear(new VerificacionCorreoRepository.Token(TokenOpaco.hash(token), u.id(), ahora.plus(AutenticarUsuarioService.VIDA_VERIFICACION), false));
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.REENVIAR_VERIFICACION, cuentaId, null, "usuario=" + u.email(), ahora));
        });
        enviar(u, CorreosDeAcceso.ASUNTO_VERIFICACION, CorreosDeAcceso.cuerpoVerificacion(urlBase, token));
        return new Destinatario(u.id(), u.email());
    }

    /** Dentro de la cuenta que dice la ruta: el usuario de otra cuenta no existe para esta pantalla. */
    private Usuario usuarioActivoDeLaCuenta(UUID cuentaId, UUID usuarioId) {
        if (cuentaId == null || usuarioId == null) throw noExiste();
        Usuario u = usuarios.buscar(usuarioId).filter(x -> x.cuentaId().equals(cuentaId)).orElseThrow(SoporteDeAccesoService::noExiste);
        if (!u.activo()) throw new DomainException("USUARIO_INACTIVO", "El usuario " + u.email() + " está desactivado");
        return u;
    }

    /** Sin SMTP el adaptador escribe en el log y no lanza: contestar «enviado» sería mentir. No se crea nada. */
    private void exigirCorreoQueEntregue() {
        if (!correo.entregaDeVerdad())
            throw new DomainException("CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: no se mandó nada");
    }

    /** Fuera de la transacción: si el servidor de correo rechaza el envío, el intento ya quedó en la bitácora y el enlace sin usar vence solo. */
    private void enviar(Usuario u, String asunto, String cuerpo) {
        try {
            correo.enviar(u.email(), asunto, cuerpo);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DomainException("CORREO_NO_ENVIADO", "No se pudo enviar el correo a " + u.email() + ": " + e.getMessage(), e);
        }
    }

    private static DomainException noExiste() { return new DomainException("NO_ENCONTRADO", "El usuario no existe en esta cuenta"); }
}
