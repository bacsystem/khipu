package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.service.LimiteDeIntentos.Ambito;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** #261: cuántos fallos de login se toleran por correo y por IP, y cuántos correos de recuperación por dirección. */
class LimiteDeIntentosTest {
    final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-08T12:00:00Z"));
    final Clock clock = new Clock() {
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return ahora.get(); }
    };
    final Fakes.Intentos intentos = new Fakes.Intentos();
    final LimiteDeIntentos limite = new LimiteDeIntentos(intentos, clock);

    void pasan(Duration d) { ahora.set(ahora.get().plus(d)); }

    void fallar(int veces, String email, String ip) {
        for (int i = 0; i < veces; i++) limite.reservarLogin(Ambito.CLIENTE, email, ip);
    }

    static void assertBloqueado(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");
    }

    @Test void cincoFallosBloqueanElCorreoQuinceMinutos() {
        fallar(5, "ana@x.pe", null);

        assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null));
        pasan(Duration.ofMinutes(14));
        assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null));
        pasan(Duration.ofMinutes(1));
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null)).doesNotThrowAnyException();
    }

    @Test void elQuintoIntentoTodaviaSeCompruebaYElSextoNo() {
        fallar(4, "ana@x.pe", null);
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null)).as("el 5.º").doesNotThrowAnyException();
        assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null));
    }

    @Test void losFallosViejosNoSeAcumulan() {
        fallar(4, "ana@x.pe", null);
        pasan(Duration.ofMinutes(16));
        fallar(4, "ana@x.pe", null);
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null)).as("4 de antes no cuentan").doesNotThrowAnyException();
    }

    @Test void unAciertoPoneElCorreoEnCero() {
        fallar(4, "ana@x.pe", null);
        limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null).acerto();
        fallar(4, "ana@x.pe", null);
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null)).doesNotThrowAnyException();
    }

    @Test void acertarEnElQuintoNoDejaElCorreoBloqueado() {
        fallar(4, "ana@x.pe", null);
        limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null).acerto();
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null)).doesNotThrowAnyException();
    }

    @Test void elCorreoSeComparaSinMayusculasNiEspacios() {
        fallar(5, "Ana@X.pe ", null);
        assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", null));
    }

    @Test void clienteYAdministradorCuentanAparte() {
        fallar(5, "ana@x.pe", null);
        assertThatCode(() -> limite.reservarLogin(Ambito.ADMINISTRADOR, "ana@x.pe", null)).doesNotThrowAnyException();
    }

    @Test void veinteFallosDesdeUnaIpLaBloqueanAunqueCambieElCorreo() {
        for (int i = 0; i < 20; i++) limite.reservarLogin(Ambito.CLIENTE, "u" + i + "@x.pe", "203.0.113.9");

        assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "otro@x.pe", "203.0.113.9"));
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "otro@x.pe", "198.51.100.1")).as("otra IP").doesNotThrowAnyException();
    }

    /** Una oficina detrás de una sola IP: sus logins correctos no gastan el cupo de fallos de la red. */
    @Test void losAciertosNoCuentanParaLaIp() {
        for (int i = 0; i < 30; i++) limite.reservarLogin(Ambito.CLIENTE, "u" + i + "@x.pe", "203.0.113.9").acerto();
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "otro@x.pe", "203.0.113.9")).doesNotThrowAnyException();
    }

    /** Un intento que se frena por el correo no llegó a probar ninguna contraseña: no gasta el cupo de la IP. */
    @Test void unCorreoBloqueadoNoGastaElCupoDeLaIp() {
        fallar(5, "ana@x.pe", null);
        for (int i = 0; i < 30; i++) assertBloqueado(() -> limite.reservarLogin(Ambito.CLIENTE, "ana@x.pe", "203.0.113.9"));
        assertThatCode(() -> limite.reservarLogin(Ambito.CLIENTE, "otro@x.pe", "203.0.113.9")).doesNotThrowAnyException();
    }

    @Test void sinIpConocidaSoloCuentaElCorreo() {
        for (int i = 0; i < 40; i++) limite.reservarLogin(Ambito.CLIENTE, "u" + i + "@x.pe", null);
        assertThat(intentos.filas.keySet()).noneMatch(k -> k.contains(":ip:"));
    }

    @Test void laPurgaBorraLoDeMasDeUnDiaYConservaLoReciente() {
        limite.reservarLogin(Ambito.CLIENTE, "viejo@x.pe", null);
        pasan(Duration.ofHours(25));
        limite.reservarLogin(Ambito.CLIENTE, "nuevo@x.pe", null);

        assertThat(limite.purgar()).isEqualTo(1);
        assertThat(intentos.filas.keySet()).containsExactly("login:cliente:nuevo@x.pe");
    }

    @Test void tresCorreosDeRecuperacionPorHora() {
        assertThat(limite.admiteRecuperacion("ana@x.pe")).isTrue();
        assertThat(limite.admiteRecuperacion("ANA@x.pe")).isTrue();
        assertThat(limite.admiteRecuperacion("ana@x.pe")).isTrue();
        assertThat(limite.admiteRecuperacion("ana@x.pe")).as("el 4.º").isFalse();
        assertThat(limite.admiteRecuperacion("beto@x.pe")).as("otra dirección").isTrue();

        pasan(Duration.ofMinutes(59));
        assertThat(limite.admiteRecuperacion("ana@x.pe")).isFalse();
        pasan(Duration.ofMinutes(1));
        assertThat(limite.admiteRecuperacion("ana@x.pe")).isTrue();
    }
}
