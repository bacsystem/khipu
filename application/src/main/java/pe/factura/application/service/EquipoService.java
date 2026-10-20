package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AceptarInvitacionUseCase;
import pe.factura.application.port.in.GestionarEquipoUseCase;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Equipo;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * El equipo de una cuenta (#21): los usuarios que tiene, con su rol, y las invitaciones para sumar a otros. El invitado crea su contraseña desde el enlace y entra con el rol
 * elegido. Las reglas (quién gestiona, el último administrador, un solo uso) están en {@link Equipo} e {@link Invitacion}; aquí, lo que hay que mirar en la base y cuándo.
 */
@RequiredArgsConstructor
public class EquipoService implements GestionarEquipoUseCase, AceptarInvitacionUseCase {
    /** Como la del alta asistida: quien recibe una invitación no la abre necesariamente hoy. */
    static final Duration VIDA_INVITACION = Duration.ofDays(7);
    /** Invitaciones pendientes por cuenta: sin tope, invitar serviría para mandar correos sin fin desde nuestro dominio. */
    static final int MAX_PENDIENTES = 20;

    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final CuentaRepository cuentas;
    private final InvitacionRepository invitaciones;
    private final SesionRepository sesiones;
    private final PasswordHasher hasher;
    private final CorreoSender correo;
    private final PlantillasDeCorreo plantillas;
    private final UnitOfWork uow;
    private final Clock clock;

    // --- Usuarios -------------------------------------------------------------------------------------------------------------------

    @Override
    public List<Usuario> miembros(UUID cuentaId, UUID actorId) {
        Equipo e = equipos.deLaCuenta(cuentaId);
        e.exigirAdministrador(actorId);
        return e.miembros();
    }

    @Override
    public Usuario cambiarRol(UUID cuentaId, UUID actorId, UUID usuarioId, Rol rol) {
        return cambiar(cuentaId, e -> e.cambiarRol(actorId, usuarioId, rol));
    }

    @Override
    public Usuario desactivar(UUID cuentaId, UUID actorId, UUID usuarioId) {
        return uow.ejecutar(() -> {
            Usuario u = cambiarDentro(cuentaId, e -> e.desactivar(actorId, usuarioId));
            // Sin esto seguiría renovando la sesión que tenía abierta; el filtro de la API, además, ya no lo deja pasar por estar inactivo.
            sesiones.revocarTodas(u.id());
            return u;
        });
    }

    @Override
    public Usuario reactivar(UUID cuentaId, UUID actorId, UUID usuarioId) {
        return cambiar(cuentaId, e -> e.reactivar(actorId, usuarioId));
    }

    private Usuario cambiar(UUID cuentaId, Function<Equipo, Usuario> cambio) {
        return uow.ejecutar(() -> cambiarDentro(cuentaId, cambio));
    }

    /** Con el equipo bloqueado: dos administradores que se quitan el rol a la vez no dejan la cuenta sin ninguno. */
    private Usuario cambiarDentro(UUID cuentaId, Function<Equipo, Usuario> cambio) {
        Equipo e = equipos.deLaCuentaParaCambiar(cuentaId);
        Usuario u = cambio.apply(e);
        if (!u.equals(e.miembro(u.id()))) usuarios.guardar(u);
        return u;
    }

    // --- Invitaciones ---------------------------------------------------------------------------------------------------------------

    @Override
    public List<Invitacion> invitaciones(UUID cuentaId, UUID actorId) {
        equipos.deLaCuenta(cuentaId).exigirAdministrador(actorId);
        return invitaciones.pendientes(cuentaId);
    }

    @Override
    public Enviada invitar(UUID cuentaId, UUID actorId, String email, Rol rol, String urlPortal) {
        String token = TokenOpaco.generar();
        Invitacion nueva = uow.ejecutar(() -> {
            // Bloqueado: dos invitaciones al mismo correo a la vez no quedan las dos pendientes.
            Equipo e = equipos.deLaCuentaParaCambiar(cuentaId);
            e.exigirAdministrador(actorId);
            Invitacion i = Invitacion.nueva(cuentaId, email, rol, TokenOpaco.hash(token), clock.instant(), VIDA_INVITACION, actorId);
            if (e.tieneCorreo(i.email())) throw new DomainException("YA_ES_MIEMBRO", "Ese correo ya es un usuario de tu cuenta");
            // Un correo es de un solo usuario en khipu, y cada usuario es de una sola cuenta.
            if (usuarios.buscarPorEmail(i.email()).isPresent())
                throw new DomainException("USUARIO_EXISTENTE", "Ese correo ya tiene un usuario en khipu y no se puede sumar a otra cuenta");
            List<Invitacion> pendientes = invitaciones.pendientes(cuentaId);
            if (pendientes.stream().anyMatch(p -> p.email().equals(i.email())))
                throw new DomainException("INVITACION_PENDIENTE", "Ya hay una invitación pendiente para ese correo: reenvíala en vez de crear otra");
            if (pendientes.size() >= MAX_PENDIENTES)
                throw new DomainException("DEMASIADAS_INVITACIONES", "Tu cuenta tiene " + MAX_PENDIENTES + " invitaciones pendientes. Revoca las que ya no hagan falta antes de invitar a alguien más");
            invitaciones.crear(i);
            return i;
        });
        return new Enviada(nueva, enviar(nueva, urlPortal, token));
    }

    @Override
    public Enviada reenviar(UUID cuentaId, UUID actorId, UUID invitacionId, String urlPortal) {
        String token = TokenOpaco.generar();
        Invitacion reenviada = uow.ejecutar(() -> {
            equipos.deLaCuenta(cuentaId).exigirAdministrador(actorId);
            Invitacion r = invitacionDe(cuentaId, invitacionId).reenviada(TokenOpaco.hash(token), clock.instant(), VIDA_INVITACION);
            guardarPendiente(r);
            return r;
        });
        return new Enviada(reenviada, enviar(reenviada, urlPortal, token));
    }

    @Override
    public void revocar(UUID cuentaId, UUID actorId, UUID invitacionId) {
        uow.ejecutar(() -> {
            equipos.deLaCuenta(cuentaId).exigirAdministrador(actorId);
            guardarPendiente(invitacionDe(cuentaId, invitacionId).revocada());
        });
    }

    /** La de otra cuenta no existe para esta: ni se dice que existe. */
    private Invitacion invitacionDe(UUID cuentaId, UUID invitacionId) {
        return invitaciones.buscar(invitacionId).filter(i -> i.cuentaId().equals(cuentaId))
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Invitación no encontrada"));
    }

    private void guardarPendiente(Invitacion i) {
        if (!invitaciones.actualizarPendiente(i))
            throw new DomainException("INVITACION_NO_PENDIENTE", "La invitación ya no está pendiente: se aceptó o se revocó en el medio");
    }

    /**
     * Fuera de la transacción y sin propagar el error: la invitación queda guardada y se puede reenviar. Devuelve si el correo salió de verdad (sin SMTP el adaptador lo
     * escribe en el log y no lanza, y eso no es una entrega). La causa de un fallo la registra el adaptador de correo.
     */
    private boolean enviar(Invitacion i, String urlPortal, String token) {
        try {
            String cuenta = cuentas.buscar(i.cuentaId()).map(c -> c.nombre()).orElse("una cuenta");
            var texto = CorreosDeAcceso.invitacionAlEquipo(plantillas, cuenta, i.rol(), urlPortal, token);
            correo.enviar(i.email(), texto.asunto(), texto.cuerpo());
            return correo.entregaDeVerdad();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // --- Lo que hace el invitado ----------------------------------------------------------------------------------------------------

    @Override
    public Detalle consultar(String token) {
        Invitacion i = aceptable(TokenOpaco.hash(token == null ? "" : token), clock.instant());
        String cuenta = cuentas.buscar(i.cuentaId()).map(c -> c.nombre()).orElseThrow(EquipoService::enlaceInvalido);
        return new Detalle(i.email(), cuenta, i.rol(), i.expiraEn());
    }

    @Override
    public void aceptar(String token, String password) {
        Usuario.validarPassword(password);
        String hash = TokenOpaco.hash(token == null ? "" : token);
        Instant ahora = clock.instant();
        Invitacion i = aceptable(hash, ahora);
        // Antes de la transacción: el hash de la contraseña es lento a propósito y no debe tener la fila tomada mientras tanto.
        String passwordHash = hasher.hash(password);
        uow.ejecutar(() -> {
            if (usuarios.buscarPorEmail(i.email()).isPresent())
                throw new DomainException("USUARIO_EXISTENTE", "Ese correo ya tiene un usuario en khipu: entra con tu contraseña o pide restablecerla");
            // Marcarla aceptada es la condición: de dos clics a la vez, uno crea el usuario y el otro ve el enlace ya usado.
            if (!invitaciones.aceptar(i.id(), hash, ahora)) throw enlaceInvalido();
            // El enlace llegó a ese correo: queda verificado, como al restablecer la contraseña.
            usuarios.guardar(new Usuario(UUID.randomUUID(), i.cuentaId(), i.email(), passwordHash, i.rol(), true, ahora));
        });
    }

    private Invitacion aceptable(String hash, Instant ahora) {
        return invitaciones.buscarPorTokenHash(hash).filter(i -> i.aceptable(ahora)).orElseThrow(EquipoService::enlaceInvalido);
    }

    private static DomainException enlaceInvalido() {
        return new DomainException("TOKEN_INVALIDO", "El enlace de la invitación es inválido, venció o ya se usó. Pide a quien te invitó que te la reenvíe");
    }
}
