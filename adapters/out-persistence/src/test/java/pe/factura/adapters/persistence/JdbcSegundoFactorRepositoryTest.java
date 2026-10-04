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
        repo.registrarFallo(admin, 5, null);

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
        repo.registrarFallo(admin, 5, null);

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

    @Test void losFallosSeSumanYAlLlegarAlTopeBloqueanYVuelvenACero() {
        repo.guardarPendiente(admin, new byte[]{1});
        Instant hasta = Instant.now().plus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        for (int i = 1; i <= 4; i++) {
            repo.registrarFallo(admin, 5, hasta);
            assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> assertThat(e.bloqueadoHasta()).isNull());
        }
        assertThat(repo.buscar(admin).orElseThrow().fallos()).isEqualTo(4);

        repo.registrarFallo(admin, 5, hasta);

        assertThat(repo.buscar(admin).orElseThrow()).satisfies(e -> {
            assertThat(e.fallos()).isZero();
            assertThat(e.bloqueadoHasta()).isEqualTo(hasta);
        });
    }

    /** Intentos en paralelo no se pisan la cuenta: 20 fallos simultáneos con tope 5 bloquean, aunque todos leyeran «0 fallos». */
    @Test void fallosSimultaneosNoSePierden() throws Exception {
        repo.guardarPendiente(admin, new byte[]{1});
        Instant hasta = Instant.now().plus(15, ChronoUnit.MINUTES);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<?>> tareas = new ArrayList<>();
        for (int i = 0; i < 20; i++) tareas.add(pool.submit(() -> { salida.await(); repo.registrarFallo(admin, 5, hasta); return null; }));
        salida.countDown();
        for (Future<?> t : tareas) t.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        Estado e = repo.buscar(admin).orElseThrow();
        assertThat(e.bloqueadoHasta()).isNotNull();
        assertThat(e.fallos()).as("20 fallos con tope 5: cuatro bloqueos exactos").isZero();
    }

    @Test void unCodigoDeRecuperacionSeConsumeUnaVezYReiniciaLosFallos() {
        repo.guardarPendiente(admin, new byte[]{1});
        repo.confirmar(admin, 1000L, List.of(hash('a'), hash('b')));
        repo.registrarFallo(admin, 5, null);
        repo.registrarFallo(admin, 5, null);

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
