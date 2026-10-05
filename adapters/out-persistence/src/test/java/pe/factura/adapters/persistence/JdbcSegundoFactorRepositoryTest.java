package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SegundoFactorRepository.Estado;
import pe.factura.domain.plataforma.Administrador;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSegundoFactorRepositoryTest extends PersistenciaTestBase {
    final JdbcSegundoFactorRepository repo = new JdbcSegundoFactorRepository(jdbc);
    UUID admin;

    @BeforeEach void unAdministrador() {
        admin = UUID.randomUUID();
        new JdbcAdministradorRepository(jdbc).guardar(new Administrador(admin, "ana@khipu.pe", "hash", true));
    }

    @Test void sinFilaNoHaySegundoFactor() {
        assertThat(repo.buscar(admin)).isEmpty();
    }

    @Test void guardaElPendienteYLoConfirmaConSusCodigos() {
        repo.guardarPendiente(admin, new byte[]{1, 2, 3});
        assertThat(repo.buscar(admin)).get().satisfies(e -> {
            assertThat(e.secretoCifrado()).containsExactly(1, 2, 3);
            assertThat(e.confirmado()).isFalse();
            assertThat(e.ultimoPaso()).isZero();
        });

        repo.confirmar(admin, 1000L, List.of(hash('a'), hash('b')));

        Estado e = repo.buscar(admin).orElseThrow();
        assertThat(e.confirmado()).isTrue();
        assertThat(e.ultimoPaso()).isEqualTo(1000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrador_codigo_recuperacion WHERE administrador_id = ? AND usado_at IS NULL", Integer.class, admin)).isEqualTo(2);
    }

    @Test void otroPendienteReemplazaAlAnteriorYBorraSusCodigos() {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of(hash('a')));
        repo.reservarIntento(admin, 5, Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));

        repo.guardarPendiente(admin, new byte[]{9});

        Estado e = repo.buscar(admin).orElseThrow();
        assertThat(e.secretoCifrado()).containsExactly(9);
        assertThat(e.confirmado()).isFalse();
        assertThat(e.ultimoPaso()).isZero();
        assertThat(e.fallos()).isZero();
        assertThat(repo.consumirCodigoRecuperacion(admin, hash('a'))).isFalse();
    }

    @Test void unPasoSoloSeAceptaSiEsPosteriorAlUltimo() {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of());
        repo.reservarIntento(admin, 5, Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThat(repo.registrarAcceso(admin, 1000L)).as("el mismo paso de la confirmación").isFalse();
        assertThat(repo.registrarAcceso(admin, 999L)).isFalse();
        assertThat(repo.buscar(admin).orElseThrow().fallos()).as("un intento rechazado no borra los fallos").isEqualTo(1);
        assertThat(repo.registrarAcceso(admin, 1001L)).isTrue();
        assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> {
            assertThat(e.ultimoPaso()).isEqualTo(1001L);
            assertThat(e.fallos()).isZero();
        });
        assertThat(repo.registrarAcceso(admin, 1001L)).isFalse();
    }

    /** Dos logins simultáneos con el mismo código: solo uno entra. La condición vive en el UPDATE, no en una lectura previa. */
    @Test void dosAccesosSimultaneosConElMismoPasoSoloAceptanUno() throws Exception {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < 8; i++) resultados.add(pool.submit(() -> { salida.await(); return repo.registrarAcceso(admin, 1001L); }));
        salida.countDown();
        int aceptados = 0;
        for (Future<Boolean> r : resultados) if (r.get(10, TimeUnit.SECONDS)) aceptados++;
        pool.shutdown();
        assertThat(aceptados).isEqualTo(1);
    }

    /** Cada intento se reserva antes de comprobar el código: los cuatro primeros solo suman, el quinto concede y bloquea. */
    @Test void losIntentosSeSumanYElQuintoBloquea() {
        repo.guardarPendiente(admin, new byte[]{1});
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant hasta = ahora.plus(15, ChronoUnit.MINUTES);
        for (int i = 1; i <= 4; i++) {
            assertThat(repo.reservarIntento(admin, 5, ahora, hasta)).as("intento %d", i).isTrue();
            assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> assertThat(e.bloqueadoHasta()).isNull());
        }
        assertThat(repo.buscar(admin).orElseThrow().fallos()).isEqualTo(4);

        assertThat(repo.reservarIntento(admin, 5, ahora, hasta)).as("el quinto todavía se concede").isTrue();

        assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> {
            assertThat(e.fallos()).isZero();
            assertThat(e.bloqueadoHasta()).isEqualTo(hasta);
        });
    }

    /** Con la cuenta bloqueada el intento se niega y no suma nada; al vencer el bloqueo vuelve a concederse. */
    @Test void conLaCuentaBloqueadaElIntentoSeNiegaHastaQueVenza() {
        repo.guardarPendiente(admin, new byte[]{1});
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant hasta = ahora.plus(15, ChronoUnit.MINUTES);
        for (int i = 0; i < 5; i++) repo.reservarIntento(admin, 5, ahora, hasta);

        assertThat(repo.reservarIntento(admin, 5, ahora.plus(14, ChronoUnit.MINUTES), hasta.plus(14, ChronoUnit.MINUTES))).as("a los 14 minutos").isFalse();
        assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> {
            assertThat(e.fallos()).as("un intento negado no suma").isZero();
            assertThat(e.bloqueadoHasta()).as("ni mueve el bloqueo").isEqualTo(hasta);
        });
        assertThat(repo.reservarIntento(admin, 5, hasta, hasta.plus(15, ChronoUnit.MINUTES))).as("al vencer").isTrue();
    }

    /**
     * El tope se aplica al reservar, no al leer: 20 peticiones simultáneas que ya leyeron «sin bloqueo» consiguen exactamente cinco intentos.
     * Con la comprobación al inicio y el fallo al final, las 20 habrían probado un código.
     */
    @Test void intentosSimultaneosConcedenExactamenteElTope() throws Exception {
        repo.guardarPendiente(admin, new byte[]{1});
        Instant ahora = Instant.now();
        Instant hasta = ahora.plus(15, ChronoUnit.MINUTES);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) tareas.add(pool.submit(() -> { salida.await(); return repo.reservarIntento(admin, 5, ahora, hasta); }));
        salida.countDown();
        int concedidos = 0;
        for (Future<Boolean> t : tareas) if (t.get(10, TimeUnit.SECONDS)) concedidos++;
        pool.shutdown();
        assertThat(concedidos).isEqualTo(5);
        assertThat(repo.buscar(admin).orElseThrow().bloqueadoHasta()).isNotNull();
    }

    /** El quinto intento deja el bloqueo puesto antes de saber si el código era bueno: un acierto lo levanta. */
    @Test void unAciertoLevantaElBloqueoQueDejoSuPropioIntento() {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of(hash('a')));
        Instant ahora = Instant.now();
        for (int i = 0; i < 5; i++) repo.reservarIntento(admin, 5, ahora, ahora.plus(15, ChronoUnit.MINUTES));
        assertThat(repo.buscar(admin).orElseThrow().bloqueadoHasta()).isNotNull();

        assertThat(repo.registrarAcceso(admin, 1001L)).isTrue();

        assertThat(repo.buscar(admin).orElseThrow().bloqueadoHasta()).as("el código de la app").isNull();

        for (int i = 0; i < 5; i++) repo.reservarIntento(admin, 5, ahora.plus(1, ChronoUnit.HOURS), ahora.plus(75, ChronoUnit.MINUTES));
        assertThat(repo.buscar(admin).orElseThrow().bloqueadoHasta()).isNotNull();

        assertThat(repo.consumirCodigoRecuperacion(admin, hash('a'))).isTrue();

        assertThat(repo.buscar(admin).orElseThrow().bloqueadoHasta()).as("el código de recuperación").isNull();
    }

    @Test void unCodigoDeRecuperacionSeConsumeUnaVezYReiniciaLosFallos() {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of(hash('a'), hash('b')));
        repo.reservarIntento(admin, 5, Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
        repo.reservarIntento(admin, 5, Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThat(repo.consumirCodigoRecuperacion(admin, hash('a'))).isTrue();
        assertThat(repo.buscar(admin).orElseThrow().fallos()).isZero();
        assertThat(repo.consumirCodigoRecuperacion(admin, hash('a'))).as("ya usado").isFalse();
        assertThat(repo.consumirCodigoRecuperacion(admin, hash('z'))).as("inexistente").isFalse();
        assertThat(repo.consumirCodigoRecuperacion(admin, hash('b'))).isTrue();
    }

    @Test void losCodigosDeOtroAdministradorNoSirven() {
        UUID otro = UUID.randomUUID();
        new JdbcAdministradorRepository(jdbc).guardar(new Administrador(otro, "luis@khipu.pe", "hash", true));
        repo.guardarPendiente(otro, new byte[]{1});
        repo.confirmar(otro, 1000L, List.of(hash('a')));
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of());

        assertThat(repo.consumirCodigoRecuperacion(admin, hash('a'))).isFalse();
    }

    private static String hash(char c) { return String.valueOf(c).repeat(64); }
}
