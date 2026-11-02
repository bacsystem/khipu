package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** #261: las reglas del contador viven en una sola sentencia, así que se cumplen también entre peticiones simultáneas. */
class JdbcIntentosDeAccesoRepositoryTest extends PersistenciaTestBase {
    static final Duration QUINCE = Duration.ofMinutes(15);
    final JdbcIntentosDeAccesoRepository repo = new JdbcIntentosDeAccesoRepository(jdbc);
    final String clave = "login:cliente:" + UUID.randomUUID() + "@x.pe";
    final Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test void losCuatroPrimerosSumanYElQuintoConcedeYBloquea() {
        for (int i = 1; i <= 5; i++) assertThat(repo.reservar(clave, 5, t0, QUINCE, QUINCE)).as("intento %d", i).isTrue();

        assertThat(repo.reservar(clave, 5, t0.plusSeconds(1), QUINCE, QUINCE)).as("el sexto").isFalse();
        assertThat(repo.reservar(clave, 5, t0.plus(Duration.ofMinutes(14)), QUINCE, QUINCE)).isFalse();
        assertThat(repo.reservar(clave, 5, t0.plus(QUINCE), QUINCE, QUINCE)).as("vencido el bloqueo").isTrue();
    }

    @Test void unIntentoNegadoNoSumaNada() {
        for (int i = 0; i < 5; i++) repo.reservar(clave, 5, t0, QUINCE, QUINCE);
        for (int i = 0; i < 10; i++) repo.reservar(clave, 5, t0.plusSeconds(i), QUINCE, QUINCE);

        assertThat(jdbc.queryForObject("SELECT intentos FROM intento_de_acceso WHERE clave = ?", Integer.class, clave)).isEqualTo(5);
    }

    @Test void vencidaLaVentanaLaCuentaEmpiezaDeCero() {
        for (int i = 0; i < 4; i++) repo.reservar(clave, 5, t0, QUINCE, QUINCE);
        Instant despues = t0.plus(Duration.ofMinutes(16));
        for (int i = 0; i < 4; i++) assertThat(repo.reservar(clave, 5, despues, QUINCE, QUINCE)).isTrue();
        assertThat(repo.reservar(clave, 5, despues, QUINCE, QUINCE)).as("el quinto de la ventana nueva aún se concede").isTrue();
        assertThat(repo.reservar(clave, 5, despues, QUINCE, QUINCE)).isFalse();
    }

    @Test void devolverRestaUnoYLevantaElBloqueoDeEseIntento() {
        for (int i = 0; i < 5; i++) repo.reservar(clave, 5, t0, QUINCE, QUINCE);
        repo.devolver(clave);
        assertThat(repo.reservar(clave, 5, t0.plusSeconds(1), QUINCE, QUINCE)).isTrue();
        assertThat(repo.reservar(clave, 5, t0.plusSeconds(1), QUINCE, QUINCE)).isFalse();
    }

    @Test void devolverNoBajaDeCero() {
        repo.devolver(clave);
        repo.reservar(clave, 5, t0, QUINCE, QUINCE);
        repo.devolver(clave);
        repo.devolver(clave);
        assertThat(jdbc.queryForObject("SELECT intentos FROM intento_de_acceso WHERE clave = ?", Integer.class, clave)).isZero();
    }

    @Test void reiniciarOlvidaLaClave() {
        for (int i = 0; i < 5; i++) repo.reservar(clave, 5, t0, QUINCE, QUINCE);
        repo.reiniciar(clave);
        assertThat(repo.reservar(clave, 5, t0, QUINCE, QUINCE)).isTrue();
    }

    @Test void purgarBorraSoloLoViejoYSinBloqueo() {
        String vieja = clave + ":vieja", bloqueada = clave + ":bloqueada";
        repo.reservar(vieja, 5, t0.minus(Duration.ofDays(2)), QUINCE, QUINCE);
        for (int i = 0; i < 5; i++) repo.reservar(bloqueada, 5, t0.minus(Duration.ofDays(2)), QUINCE, Duration.ofDays(3));
        repo.reservar(clave, 5, t0, QUINCE, QUINCE);

        repo.purgar(t0.minus(Duration.ofDays(1)));

        assertThat(jdbc.queryForList("SELECT clave FROM intento_de_acceso WHERE clave LIKE ?", String.class, clave + "%"))
                .containsExactlyInAnyOrder(clave, bloqueada);
    }

    /** 20 peticiones simultáneas contra un tope de 5: solo 5 llegan a comprobar la contraseña. */
    @Test void veintePeticionesSimultaneasSoloConcedenCinco() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 20; i++) resultados.add(pool.submit(() -> { salida.await(); return repo.reservar(clave, 5, t0, QUINCE, QUINCE); }));
        salida.countDown();
        int concedidos = 0;
        for (Future<Boolean> r : resultados) if (r.get(10, TimeUnit.SECONDS)) concedidos++;
        pool.shutdown();
        assertThat(concedidos).isEqualTo(5);
    }
}
