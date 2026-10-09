package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;
import pe.factura.application.port.out.*;
import pe.factura.application.port.out.SegundoFactorRepository.Estado;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.Administrador;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Login del backoffice en dos pasos (#177). La contraseña solo da un desafío; la sesión sale del segundo factor (TOTP de una app de
 * autenticación, o un código de recuperación). Un administrador sin segundo factor no puede operar: el desafío solo le sirve para
 * configurarlo, y configurarlo ya es iniciar sesión.
 */
@RequiredArgsConstructor
public class AutenticarAdministradorService implements AutenticarAdministradorUseCase {
    /** Seis dígitos son un millón de combinaciones: con este tope, probarlas todas lleva años. */
    static final int MAX_FALLOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    static final int CODIGOS_RECUPERACION = 10;
    /** Sin 0/O ni 1/I: el código se copia a mano de un papel. 10 caracteres de 32 símbolos son 50 bits. */
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom AZAR = new SecureRandom();

    private final AdministradorRepository administradores;
    private final PasswordHasher hasher;
    private final AdministradorTokenEmisor tokens;
    private final SegundoFactorRepository factores;
    private final SegundoFactor totp;
    private final SecretCipher cifrador;
    private final CodigoQr qr;
    private final UnitOfWork uow;
    private final AuditoriaAdminRepository auditoria;
    private final Clock clock;
    private final LimiteDeIntentos limite;

    @Override
    public Desafio login(String email, String password, String ip) {
        // El segundo factor tiene su propio tope; sin este, la contraseña se podía probar sin freno (#261).
        var intento = limite.reservarLogin(LimiteDeIntentos.Ambito.ADMINISTRADOR, email, ip);
        Administrador a = administradores.buscarPorEmail(email == null ? "" : email.trim().toLowerCase())
                .filter(Administrador::activo)
                .filter(x -> hasher.coincide(password == null ? "" : password, x.passwordHash()))
                .orElseThrow(() -> new DomainException("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos"));
        intento.acerto();
        boolean configurado = factores.buscar(a.id()).map(Estado::confirmado).orElse(false);
        return new Desafio(tokens.emitirDesafio(a.id()), configurado ? Paso.VERIFICAR_SEGUNDO_FACTOR : Paso.CONFIGURAR_SEGUNDO_FACTOR);
    }

    @Override
    public Configuracion configurarSegundoFactor(String desafio) {
        Administrador a = delDesafio(desafio);
        // Con el segundo factor ya confirmado, la contraseña sola no puede cambiarlo por el de otro dispositivo.
        if (factores.buscar(a.id()).map(Estado::confirmado).orElse(false)) throw yaConfigurado();
        String secreto = totp.nuevoSecreto();
        factores.guardarPendiente(a.id(), cifrador.cifrar(secreto.getBytes(StandardCharsets.UTF_8)));
        String uri = totp.uri(secreto, a.email());
        return new Configuracion(secreto, uri, qr.png(uri));
    }

    @Override
    public SesionNueva confirmarSegundoFactor(String desafio, String codigo, String ip) {
        Administrador a = delDesafio(desafio);
        Estado e = factores.buscar(a.id()).orElseThrow(AutenticarAdministradorService::noConfigurado);
        if (e.confirmado()) throw yaConfigurado();
        reservarIntento(a.id());
        OptionalLong paso = totp.paso(secreto(e), codigo == null ? "" : codigo.trim(), Instant.now(clock));
        if (paso.isEmpty()) throw codigoInvalido();

        List<String> codigos = new ArrayList<>();
        while (codigos.size() < CODIGOS_RECUPERACION) {
            String c = codigoRecuperacion();
            if (!codigos.contains(c)) codigos.add(c);
        }
        Sesion sesion = uow.ejecutar(() -> {
            factores.confirmar(a.id(), paso.getAsLong(), codigos.stream().map(AutenticarAdministradorService::hash).toList());
            auditoria.registrar(RegistroAuditoria.de(ActorAdmin.administrador(a.id(), ip), AccionAdmin.CONFIGURAR_SEGUNDO_FACTOR, null, null,
                    "segundo_factor=app", Instant.now(clock)));
            return sesion(a);
        });
        return new SesionNueva(sesion, List.copyOf(codigos));
    }

    @Override
    public Sesion verificarSegundoFactor(String desafio, String codigo, String ip) {
        Administrador a = delDesafio(desafio);
        Estado e = factores.buscar(a.id()).filter(Estado::confirmado).orElseThrow(AutenticarAdministradorService::noConfigurado);
        reservarIntento(a.id());
        String limpio = codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        Optional<Sesion> sesion = uow.ejecutar(() -> {
            String via;
            OptionalLong paso = limpio.matches("\\d{6}") ? totp.paso(secreto(e), limpio, Instant.now(clock)) : OptionalLong.empty();
            if (paso.isPresent() && factores.registrarAcceso(a.id(), paso.getAsLong())) via = "app";
            else if (limpio.length() == 10 && factores.consumirCodigoRecuperacion(a.id(), hash(limpio))) via = "codigo_recuperacion";
            else return Optional.<Sesion>empty();
            auditoria.registrar(RegistroAuditoria.de(ActorAdmin.administrador(a.id(), ip), AccionAdmin.INICIAR_SESION, null, null,
                    "segundo_factor=" + via, Instant.now(clock)));
            return Optional.of(sesion(a));
        });
        // El intento ya se contó al reservarlo, fuera de la transacción: lanzado dentro, se revertiría con ella y el bloqueo nunca llegaría.
        return sesion.orElseThrow(AutenticarAdministradorService::codigoInvalido);
    }

    @Override
    public Administrador me(UUID administradorId) {
        return administradores.buscar(administradorId)
                .orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Administrador no encontrado"));
    }

    /** El desafío es de corta vida, pero el administrador puede haberse desactivado mientras tanto: se vuelve a comprobar. */
    private Administrador delDesafio(String desafio) {
        return tokens.verificarDesafio(desafio)
                .flatMap(administradores::buscar)
                .filter(Administrador::activo)
                .orElseThrow(() -> new DomainException("SESION_INVALIDA", "El inicio de sesión venció o no es válido; vuelve a ingresar tu contraseña"));
    }

    private Sesion sesion(Administrador a) {
        return new Sesion(tokens.emitir(new AdministradorTokenEmisor.Claims(a.id(), a.email())), tokens.vidaSesionSegundos(), a);
    }

    private String secreto(Estado e) { return new String(cifrador.descifrar(e.secretoCifrado()), StandardCharsets.UTF_8); }

    /**
     * Reserva un intento ANTES de mirar el código, y es la única compuerta: el repositorio lo niega (sin sumar nada) si la cuenta está
     * bloqueada y, si lo concede, lo cuenta en la misma sentencia. Comprobar el bloqueo con un estado leído antes y contar el fallo
     * después dejaría que N peticiones simultáneas probaran N códigos antes de que ninguna llegara a bloquear. Se invoca fuera de la
     * transacción de la petición: dentro, se revertiría con el error y el bloqueo nunca llegaría.
     */
    private void reservarIntento(UUID administradorId) {
        Instant ahora = Instant.now(clock);
        if (!factores.reservarIntento(administradorId, MAX_FALLOS, ahora, ahora.plus(BLOQUEO)))
            throw new DomainException("DEMASIADOS_INTENTOS", "Demasiados códigos incorrectos. Espera unos minutos antes de volver a intentarlo");
    }

    private static DomainException codigoInvalido() {
        return new DomainException("CODIGO_INVALIDO", "El código no es válido");
    }

    private static String codigoRecuperacion() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(ALFABETO[AZAR.nextInt(ALFABETO.length)]);
        }
        return sb.toString();
    }

    /** SHA-256 y no bcrypt: un código de 50 bits al azar no se adivina por diccionario, y se compara contra los diez de la cuenta. */
    static String hash(String codigo) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(codigo.replace("-", "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static DomainException noConfigurado() {
        return new DomainException("SEGUNDO_FACTOR_NO_CONFIGURADO", "El segundo factor no está configurado");
    }

    private static DomainException yaConfigurado() {
        return new DomainException("SEGUNDO_FACTOR_YA_CONFIGURADO", "El segundo factor ya está configurado");
    }
}
