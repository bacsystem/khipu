package pe.factura.application.service;

import pe.factura.application.port.in.PurgarIntentosDeAccesoUseCase;
import pe.factura.application.port.out.IntentosDeAccesoRepository;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Cuántas veces se puede probar una contraseña y cuántos correos de recuperación se mandan (#261). Lo usan el login del portal,
 * el del backoffice y la recuperación de contraseña; los números son los del issue.
 * <ul>
 *   <li><b>Por correo:</b> 5 contraseñas en 15 minutos bloquean ese correo 15 minutos, exista o no la cuenta (la respuesta no dice
 *   qué cuentas hay). Cliente y administrador cuentan aparte. Un acierto lo pone en cero.</li>
 *   <li><b>Por IP:</b> 20 fallos en 15 minutos desde una IP la bloquean 15 minutos, aunque cambie el correo. Solo cuentan los fallos:
 *   un acierto devuelve su intento, para que una oficina detrás de una sola IP no se quede sin entrar. Sin IP conocida (ver
 *   {@code IpDelCliente}) no se cuenta: todas las peticiones del portal compartirían la suya y un atacante bloquearía a todos.</li>
 *   <li><b>Recuperación:</b> 3 correos por dirección por hora; pasado eso no se manda nada, en silencio.</li>
 * </ul>
 * El intento se reserva <b>antes</b> de comprobar la contraseña y fuera de cualquier transacción (ver
 * {@link IntentosDeAccesoRepository#reservar}).
 */
public class LimiteDeIntentos implements PurgarIntentosDeAccesoUseCase {
    /** Un contador sin actividad ni bloqueo desde hace más que esto ya no limita nada: su ventana más larga es de una hora. */
    static final Duration ANTIGUEDAD_PURGA = Duration.ofDays(1);
    static final int MAX_POR_CORREO = 5;
    static final int MAX_POR_IP = 20;
    static final Duration VENTANA = Duration.ofMinutes(15);
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    static final int MAX_RECUPERACIONES = 3;
    static final Duration VENTANA_RECUPERACION = Duration.ofHours(1);

    public enum Ambito { CLIENTE, ADMINISTRADOR }

    private final IntentosDeAccesoRepository intentos;
    private final Clock clock;

    public LimiteDeIntentos(IntentosDeAccesoRepository intentos, Clock clock) {
        this.intentos = intentos;
        this.clock = clock;
    }

    /**
     * Reserva un intento de login. {@code 429 DEMASIADOS_INTENTOS_LOGIN} si el correo o la IP están bloqueados; si no, quien llama
     * comprueba la contraseña y, si acierta, llama a {@link Intento#acerto()}. Un fallo no necesita nada más: ya se contó.
     *
     * @param ip la IP del cliente ya resuelta, o {@code null} si no se conoce.
     */
    public Intento reservarLogin(Ambito ambito, String email, String ip) {
        Instant ahora = clock.instant();
        String porIp = ip == null || ip.isBlank() ? null : "login:ip:" + ip.strip();
        if (porIp != null && !intentos.reservar(porIp, MAX_POR_IP, ahora, VENTANA, BLOQUEO)) throw bloqueado();
        String porCorreo = "login:" + ambito.name().toLowerCase(Locale.ROOT) + ":" + normalizar(email);
        if (!intentos.reservar(porCorreo, MAX_POR_CORREO, ahora, VENTANA, BLOQUEO)) {
            // Frenado por el correo, este intento no probó ninguna contraseña: no gasta el cupo de la IP.
            if (porIp != null) intentos.devolver(porIp);
            throw bloqueado();
        }
        return new Intento(porCorreo, porIp);
    }

    /** Si todavía se puede mandar un correo de recuperación a esta dirección; si sí, ya quedó contado. */
    public boolean admiteRecuperacion(String email) {
        return intentos.reservar("recuperar:" + normalizar(email), MAX_RECUPERACIONES, clock.instant(), VENTANA_RECUPERACION, VENTANA_RECUPERACION);
    }

    @Override
    public int purgar() {
        return intentos.purgar(clock.instant().minus(ANTIGUEDAD_PURGA));
    }

    /** Lo que queda pendiente de un intento reservado. */
    public final class Intento {
        private final String porCorreo;
        private final String porIp;

        private Intento(String porCorreo, String porIp) {
            this.porCorreo = porCorreo;
            this.porIp = porIp;
        }

        /** La contraseña era la correcta: los fallos de ese correo se olvidan y el intento no cuenta para la IP. */
        public void acerto() {
            intentos.reiniciar(porCorreo);
            if (porIp != null) intentos.devolver(porIp);
        }
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static DomainException bloqueado() {
        return new DomainException("DEMASIADOS_INTENTOS_LOGIN", "Demasiados intentos fallidos. Espera 15 minutos antes de volver a intentarlo");
    }
}
