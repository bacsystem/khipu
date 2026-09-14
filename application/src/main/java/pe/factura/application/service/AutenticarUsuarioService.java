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

    private final CuentaRepository cuentas;
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordHasher hasher;
    private final TokenEmisor tokens;
    private final CorreoSender correo;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override
    public Tokens registrar(String nombreCuenta, String email, String password) {
        Usuario.validarPassword(password);
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), nombreCuenta, email);
        if (cuentas.buscarPorEmail(cuenta.email()).isPresent() || usuarios.buscarPorEmail(cuenta.email()).isPresent())
            throw new DomainException("DUPLICADO", "Ya existe una cuenta con ese correo");
        Usuario usuario = new Usuario(UUID.randomUUID(), cuenta.id(), email, hasher.hash(password), Rol.ADMIN, true);
        return uow.ejecutar(() -> {
            cuentas.guardar(cuenta);
            usuarios.guardar(usuario);
            return emitirTokens(usuario);
        });
    }

    @Override
    public Tokens login(String email, String password) {
        Usuario u = usuarios.buscarPorEmail(email == null ? "" : email.trim().toLowerCase())
                .filter(Usuario::activo)
                .filter(x -> hasher.coincide(password == null ? "" : password, x.passwordHash()))
                .orElseThrow(() -> new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        return uow.ejecutar(() -> emitirTokens(u));
    }

    @Override
    public Tokens refrescar(String refresh) {
        Sesion s = sesiones.buscarPorRefreshHash(TokenOpaco.hash(refresh == null ? "" : refresh))
                .filter(x -> !x.revocada() && x.expiraEn().isAfter(clock.instant()))
                .orElseThrow(() -> new DomainException("SESION_INVALIDA", "Sesión expirada o inválida"));
        Usuario u = usuarios.buscar(s.usuarioId()).filter(Usuario::activo)
                .orElseThrow(() -> new DomainException("SESION_INVALIDA", "Usuario inactivo"));
        return uow.ejecutar(() -> { sesiones.revocar(s.id()); return emitirTokens(u); });   // rotación
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
            correo.enviar(u.email(), "Restablecer contraseña",
                    "Para restablecer tu contraseña abre este enlace (válido 1 hora):\n" + urlBase + "/restablecer/" + token);
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
            usuarios.guardar(u.conPasswordHash(hasher.hash(nuevaPassword)));
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
