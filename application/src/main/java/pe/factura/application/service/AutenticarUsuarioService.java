package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;
import pe.factura.application.port.out.*;
import pe.factura.application.port.out.SesionRepository.Sesion;
import pe.factura.application.port.out.SesionRepository.TokenRecuperacion;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@RequiredArgsConstructor
public class AutenticarUsuarioService implements AutenticarUsuarioUseCase {
    static final Duration VIDA_REFRESH = Duration.ofDays(30);
    static final Duration VIDA_RECUPERACION = Duration.ofHours(1);
    /** Un día: quien se registra puede no abrir el correo enseguida; pasado eso pide otro desde el portal. */
    static final Duration VIDA_VERIFICACION = Duration.ofHours(24);
    /** Enlaces de verificación por usuario dentro de {@link #VIDA_VERIFICACION}, contando el del registro. */
    static final int MAX_ENLACES_POR_DIA = 5;

    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordHasher hasher;
    private final TokenEmisor tokens;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final Clock clock;
    private final VerificacionCorreoRepository verificaciones;
    private final SuspensionRepository suspensiones;

    @Override
    public Tokens registrar(String nombreCuenta, String email, String password, String telefono, String urlBase) {
        Usuario.validarPassword(password);
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), nombreCuenta, email, telefono);
        if (cuentas.buscarPorEmail(cuenta.email()).isPresent() || usuarios.buscarPorEmail(cuenta.email()).isPresent())
            throw new DomainException("DUPLICADO", "Ya existe una cuenta con ese correo");
        Usuario usuario = new Usuario(UUID.randomUUID(), cuenta.id(), email, hasher.hash(password), Rol.ADMIN, true);
        String verificacion = TokenOpaco.generar();
        Tokens t = uow.ejecutar(() -> {
            cuentas.guardar(cuenta);
            usuarios.guardar(usuario);
            verificaciones.crear(new VerificacionCorreoRepository.Token(TokenOpaco.hash(verificacion), usuario.id(), clock.instant().plus(VIDA_VERIFICACION), false));
            return emitirTokens(usuario);
        });
        enviarVerificacion(usuario.email(), urlBase, verificacion);
        return t;
    }

    @Override
    public void verificarCorreo(String token) {
        String hash = TokenOpaco.hash(token == null ? "" : token);
        VerificacionCorreoRepository.Token t = verificaciones.buscar(hash)
                .filter(x -> !x.usado() && x.expiraEn().isAfter(clock.instant()))
                .orElseThrow(() -> new DomainException("TOKEN_INVALIDO", "El enlace de verificación es inválido o venció. Pide otro desde el portal"));
        uow.ejecutar(() -> {
            // Marcarlo usado es la condición: si dos clics llegan a la vez, solo uno verifica, y el otro ve el enlace ya usado.
            if (!verificaciones.usar(hash))
                throw new DomainException("TOKEN_INVALIDO", "El enlace de verificación ya se usó");
            // Solo la marca, sin reescribir el usuario leído: un restablecer que entre a la vez no pierde la contraseña nueva.
            usuarios.marcarCorreoVerificado(t.usuarioId(), clock.instant());
        });
    }

    @Override
    public void reenviarVerificacion(UUID usuarioId, String urlBase) {
        Usuario u = usuarios.buscar(usuarioId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Usuario no encontrado"));
        if (u.correoVerificado()) throw new DomainException("CORREO_YA_VERIFICADO", "Tu correo ya está verificado");
        // Sin verificar, el correo puede no ser suyo: sin tope, el reenvío serviría para llenar el buzón de otro desde nuestro dominio.
        if (verificaciones.contarSinVencer(u.id(), clock.instant()) >= MAX_ENLACES_POR_DIA)
            throw new DomainException("DEMASIADOS_ENLACES", "Ya te enviamos varios enlaces hoy. Revisa tu correo, también la carpeta de spam, o vuelve a pedirlo mañana");
        String verificacion = TokenOpaco.generar();
        uow.ejecutar(() -> verificaciones.crear(new VerificacionCorreoRepository.Token(TokenOpaco.hash(verificacion), u.id(), clock.instant().plus(VIDA_VERIFICACION), false)));
        enviarVerificacion(u.email(), urlBase, verificacion);
    }

    /**
     * Fuera de la transacción y sin propagar el error: un SMTP caído no debe impedir registrarse, y el usuario pide otro enlace desde
     * el portal. La causa la registra el adaptador de correo, que es quien la conoce.
     */
    private void enviarVerificacion(String email, String urlBase, String token) {
        try {
            correo.enviar(email, CorreosDeAcceso.ASUNTO_VERIFICACION, CorreosDeAcceso.cuerpoVerificacion(urlBase, token));
        } catch (RuntimeException e) {
            // el portal muestra «revisa tu correo» con un botón para pedir otro enlace
        }
    }

    @Override
    public Tokens login(String email, String password) {
        Usuario u = usuarios.buscarPorEmail(email == null ? "" : email.trim().toLowerCase())
                .filter(Usuario::activo)
                .filter(x -> hasher.coincide(password == null ? "" : password, x.passwordHash()))
                .orElseThrow(() -> new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        // Después de comprobar la contraseña: quien no se identificó no debe enterarse de si la cuenta existe ni de si está suspendida (#182).
        exigirCuentaActiva(u);
        return uow.ejecutar(() -> emitirTokens(u));
    }

    @Override
    public Tokens refrescar(String refresh) {
        Sesion s = sesiones.buscarPorRefreshHash(TokenOpaco.hash(refresh == null ? "" : refresh))
                .filter(x -> !x.revocada() && x.expiraEn().isAfter(clock.instant()))
                .orElseThrow(() -> new DomainException("SESION_INVALIDA", "Sesión expirada o inválida"));
        Usuario u = usuarios.buscar(s.usuarioId()).filter(Usuario::activo)
                .orElseThrow(() -> new DomainException("SESION_INVALIDA", "Usuario inactivo"));
        // Sin tocar la sesión: suspender no borra nada, y al reactivar la misma sesión vuelve a servir (#182).
        exigirCuentaActiva(u);
        return uow.ejecutar(() -> { sesiones.revocar(s.id()); return emitirTokens(u); });   // rotación
    }

    private void exigirCuentaActiva(Usuario u) {
        if (suspensiones.cuentaSuspendida(u.cuentaId()))
            throw new DomainException("CUENTA_SUSPENDIDA", "Tu cuenta está suspendida. Contacta a soporte para reactivarla");
    }

    @Override
    public void logout(String refresh) {
        if (refresh == null || refresh.isBlank()) return;
        sesiones.buscarPorRefreshHash(TokenOpaco.hash(refresh)).ifPresent(s -> uow.ejecutar(() -> sesiones.revocar(s.id())));
    }

    @Override
    public Usuario me(UUID usuarioId) {
        return usuarios.buscar(usuarioId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Usuario no encontrado"));
    }

    @Override
    public void solicitarRecuperacion(String email, String urlBase) {
        usuarios.buscarPorEmail(email == null ? "" : email.trim().toLowerCase()).filter(Usuario::activo).ifPresent(u -> {
            String token = TokenOpaco.generar();
            uow.ejecutar(() -> sesiones.crearRecuperacion(new TokenRecuperacion(TokenOpaco.hash(token), u.id(), clock.instant().plus(VIDA_RECUPERACION), false)));
            correo.enviar(u.email(), CorreosDeAcceso.ASUNTO_RECUPERACION, CorreosDeAcceso.cuerpoRecuperacion(urlBase, token));
        });
    }

    @Override
    public void restablecer(String token, String nuevaPassword) {
        Usuario.validarPassword(nuevaPassword);
        String hash = TokenOpaco.hash(token == null ? "" : token);
        TokenRecuperacion t = sesiones.buscarRecuperacion(hash)
                .filter(x -> !x.usado() && x.expiraEn().isAfter(clock.instant()))
                .orElseThrow(() -> new DomainException("TOKEN_INVALIDO", "Enlace de recuperación inválido o vencido"));
        Usuario u = usuarios.buscar(t.usuarioId()).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Usuario no encontrado"));
        uow.ejecutar(() -> {
            // El enlace llegó a su correo: abrirlo también lo verifica (#22). Así quien entra por la invitación del alta asistida queda verificado.
            usuarios.guardar(u.conPasswordHash(hasher.hash(nuevaPassword)).conCorreoVerificado(clock.instant()));
            sesiones.marcarRecuperacionUsada(hash);
            sesiones.revocarTodas(u.id());
        });
    }

    private Tokens emitirTokens(Usuario u) {
        String refresh = TokenOpaco.generar();
        sesiones.crear(new Sesion(UUID.randomUUID(), u.id(), TokenOpaco.hash(refresh), clock.instant().plus(VIDA_REFRESH), false));
        String access = tokens.emitir(new TokenEmisor.Claims(u.id(), u.cuentaId(), u.rol()));
        return new Tokens(access, refresh, u);
    }
}
