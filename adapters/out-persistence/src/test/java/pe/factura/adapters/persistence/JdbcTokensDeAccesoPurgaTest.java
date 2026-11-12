package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** #634: la limpieza borra los enlaces de recuperación y de verificación vencidos antes del corte, usados o no, por lotes y sin tocar los vigentes. */
class JdbcTokensDeAccesoPurgaTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-11-12T12:00:00Z");
    static final Instant CORTE = AHORA.minus(Duration.ofDays(7));

    final JdbcSesionRepository sesiones = new JdbcSesionRepository(jdbc);
    final JdbcVerificacionCorreoRepository verificaciones = new JdbcVerificacionCorreoRepository(jdbc);

    UUID usuario;

    @BeforeEach void usuario() {
        UUID cuenta = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono) VALUES (?, 'Cuenta', ?, '987654321')", cuenta, cuenta + "@x.pe");
        usuario = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id, cuenta_id, email, password_hash, rol) VALUES (?, ?, ?, 'hash', 'ADMIN')", usuario, cuenta, usuario + "@x.pe");
    }

    String recuperacion(Instant expira, boolean usado) { return token("token_recuperacion", expira, usado); }

    String verificacion(Instant expira, boolean usado) { return token("token_verificacion", expira, usado); }

    private String token(String tabla, Instant expira, boolean usado) {
        String hash = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO " + tabla + " (token_hash, usuario_id, expira_en, usado) VALUES (?, ?, ?, ?)", hash, usuario, Timestamp.from(expira), usado);
        return hash;
    }

    Instant vencidoHace(long dias) { return AHORA.minus(Duration.ofDays(dias)); }

    boolean existe(String tabla, String hash) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE token_hash = ?", Integer.class, hash) == 1;
    }

    int filas(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class); }

    @Test void borraLasRecuperacionesVencidasAntesDelCorteUsadasOno() {
        String sinUsar = recuperacion(vencidoHace(8), false);
        String usada = recuperacion(vencidoHace(9), true);

        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 1000)).isEqualTo(2);

        assertThat(existe("token_recuperacion", sinUsar)).isFalse();
        assertThat(existe("token_recuperacion", usada)).isFalse();
    }

    @Test void noTocaLasRecuperacionesVigentesNiLasVencidasDentroDelPlazo() {
        String vigente = recuperacion(AHORA.plus(Duration.ofHours(1)), false);
        String vigenteUsada = recuperacion(AHORA.plus(Duration.ofHours(1)), true);
        String invitacionVigente = recuperacion(AHORA.plus(Duration.ofDays(7)), false);
        String dentroDelPlazo = recuperacion(vencidoHace(6), false);
        String dentroDelPlazoUsada = recuperacion(vencidoHace(3), true);

        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 1000)).isZero();

        for (String hash : new String[]{vigente, vigenteUsada, invitacionVigente, dentroDelPlazo, dentroDelPlazoUsada})
            assertThat(existe("token_recuperacion", hash)).isTrue();
    }

    @Test void borraLasVerificacionesVencidasAntesDelCorteUsadasOno() {
        String sinUsar = verificacion(vencidoHace(8), false);
        String usada = verificacion(vencidoHace(30), true);

        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 1000)).isEqualTo(2);

        assertThat(existe("token_verificacion", sinUsar)).isFalse();
        assertThat(existe("token_verificacion", usada)).isFalse();
    }

    /** El «ya se usó» de un enlace vigente depende de que la fila siga ahí marcada como usada. */
    @Test void unaVerificacionVigenteYUsadaNoSeBorra() {
        String vigenteUsada = verificacion(AHORA.plus(Duration.ofHours(20)), true);
        String vigente = verificacion(AHORA.plus(Duration.ofHours(20)), false);
        String dentroDelPlazo = verificacion(vencidoHace(6), true);

        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 1000)).isZero();

        assertThat(existe("token_verificacion", vigenteUsada)).isTrue();
        assertThat(existe("token_verificacion", vigente)).isTrue();
        assertThat(existe("token_verificacion", dentroDelPlazo)).isTrue();
    }

    /** Una recuperación vigente y usada tampoco se borra: la respuesta de «restablecer» no cambia, pero la fila vigente no es de la limpieza. */
    @Test void unaRecuperacionVigenteYUsadaNoSeBorra() {
        String vigenteUsada = recuperacion(AHORA.plus(Duration.ofMinutes(30)), true);

        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 1000)).isZero();

        assertThat(existe("token_recuperacion", vigenteUsada)).isTrue();
    }

    /** El tope de reenvíos cuenta los enlaces sin vencer: la purga solo toma vencidos hace más que la retención, así que el conteo da igual antes y después. */
    @Test void elConteoDeEnlacesSinVencerNoCambiaConLaPurga() {
        verificacion(AHORA.plus(Duration.ofHours(10)), false);
        verificacion(AHORA.plus(Duration.ofHours(10)), true);
        verificacion(vencidoHace(10), false);
        int antes = verificaciones.contarSinVencer(usuario, AHORA);

        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 1000)).isEqualTo(1);

        assertThat(verificaciones.contarSinVencer(usuario, AHORA)).isEqualTo(antes).isEqualTo(2);
    }

    /** El adaptador de recuperaciones es también la PurgaDeSesiones: si los métodos se llamaran igual, Java los uniría y esta purga borraría sesiones. */
    @Test void purgarRecuperacionesNoBorraFilasDeSesionNiDeVerificacion() {
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, revocada, created_at) VALUES (?, ?, ?, ?, false, ?)",
                UUID.randomUUID(), usuario, "a".repeat(64), Timestamp.from(vencidoHace(40)), Timestamp.from(vencidoHace(70)));
        jdbc.update("INSERT INTO sesion (id, usuario_id, refresh_hash, expira_en, revocada, created_at) VALUES (?, ?, ?, ?, false, ?)",
                UUID.randomUUID(), usuario, "b".repeat(64), Timestamp.from(vencidoHace(30)), Timestamp.from(vencidoHace(60)));
        verificacion(vencidoHace(20), false);
        recuperacion(vencidoHace(20), false);

        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 1000)).isEqualTo(1);

        assertThat(filas("sesion")).as("la tabla sesion queda intacta").isEqualTo(2);
        assertThat(filas("token_verificacion")).isEqualTo(1);
        assertThat(filas("token_recuperacion")).isZero();
    }

    @Test void purgarVerificacionesNoBorraFilasDeRecuperacion() {
        recuperacion(vencidoHace(20), false);
        verificacion(vencidoHace(20), false);

        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 1000)).isEqualTo(1);

        assertThat(filas("token_recuperacion")).isEqualTo(1);
        assertThat(filas("token_verificacion")).isZero();
    }

    @Test void borraComoMuchoUnLoteYEmpiezaPorLasMasViejasEnCadaTabla() {
        String recMasVieja = recuperacion(vencidoHace(30), false), recMedia = recuperacion(vencidoHace(20), false), recNueva = recuperacion(vencidoHace(10), false);
        String verMasVieja = verificacion(vencidoHace(30), false), verMedia = verificacion(vencidoHace(20), false), verNueva = verificacion(vencidoHace(10), false);

        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 2)).isEqualTo(2);
        assertThat(existe("token_recuperacion", recMasVieja)).isFalse();
        assertThat(existe("token_recuperacion", recMedia)).isFalse();
        assertThat(existe("token_recuperacion", recNueva)).isTrue();
        assertThat(sesiones.purgarRecuperacionesVencidas(CORTE, 2)).isEqualTo(1);

        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 2)).isEqualTo(2);
        assertThat(existe("token_verificacion", verMasVieja)).isFalse();
        assertThat(existe("token_verificacion", verMedia)).isFalse();
        assertThat(existe("token_verificacion", verNueva)).isTrue();
        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 2)).isEqualTo(1);
        assertThat(verificaciones.purgarVerificacionesVencidas(CORTE, 2)).as("idempotente").isZero();
    }

    /** Con dos transacciones abiertas a la vez, cada una toma filas distintas: sin SKIP LOCKED la segunda esperaría a la primera. */
    @Test void dosTransaccionesAbiertasALaVezBorranRecuperacionesDistintas() throws Exception {
        for (int i = 0; i < 6; i++) recuperacion(vencidoHace(20 + i), false);
        recuperacion(AHORA.plus(Duration.ofHours(1)), false);

        asegurarDosTransacciones(() -> sesiones.purgarRecuperacionesVencidas(CORTE, 4), () -> sesiones.purgarRecuperacionesVencidas(CORTE, 50));

        assertThat(filas("token_recuperacion")).isEqualTo(1);
    }

    @Test void dosTransaccionesAbiertasALaVezBorranVerificacionesDistintas() throws Exception {
        for (int i = 0; i < 6; i++) verificacion(vencidoHace(20 + i), false);
        verificacion(AHORA.plus(Duration.ofHours(1)), false);

        asegurarDosTransacciones(() -> verificaciones.purgarVerificacionesVencidas(CORTE, 4), () -> verificaciones.purgarVerificacionesVencidas(CORTE, 50));

        assertThat(filas("token_verificacion")).isEqualTo(1);
    }

    /** La primera borra 4 y sigue con la transacción abierta (filas bloqueadas) hasta que la segunda terminó; la segunda no la espera ni repite sus filas. */
    private void asegurarDosTransacciones(java.util.function.IntSupplier primeraPurga, java.util.function.IntSupplier segundaPurga) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch primeraBorro = new CountDownLatch(1), segundaTermino = new CountDownLatch(1);
            Future<Integer> primera = pool.submit(() -> uow.ejecutar(() -> {
                int mias = primeraPurga.getAsInt();
                primeraBorro.countDown();
                try { segundaTermino.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return mias;
            }));
            assertThat(primeraBorro.await(30, TimeUnit.SECONDS)).isTrue();
            int segunda;
            try {
                segunda = pool.submit(() -> uow.ejecutar(segundaPurga::getAsInt)).get(20, TimeUnit.SECONDS);
            } finally {
                segundaTermino.countDown();
            }

            assertThat(primera.get(30, TimeUnit.SECONDS)).isEqualTo(4);
            assertThat(segunda).as("la segunda no esperó a la primera ni repitió sus filas").isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
