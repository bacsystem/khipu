package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import pe.factura.application.port.out.IdempotenciaRepository.Registro;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIdempotenciaRepositoryTest extends PersistenciaTestBase {
    static final String H1 = "a".repeat(64), H2 = "b".repeat(64);
    final JdbcIdempotenciaRepository repo = new JdbcIdempotenciaRepository(jdbc);

    @BeforeEach void limpiarClaves() { jdbc.update("TRUNCATE idempotencia"); }

    /** Lo registrado sin la fecha de la reserva, que es la del servidor y no se puede fijar: la comprueba su propio test. */
    static Optional<Registro> sinFecha(Optional<Registro> r) {
        return r.map(x -> new Registro(x.huella(), x.recursoId(), x.respuestaCifrada(), null));
    }

    @Test void laPrimeraVezReservaYLaSegundaDevuelveLoRegistrado() {
        UUID recurso = UUID.randomUUID();
        assertThat(repo.reservar("factura:t1", "k1", H1)).isEmpty();
        repo.completar("factura:t1", "k1", recurso);

        assertThat(sinFecha(repo.reservar("factura:t1", "k1", H2))).contains(new Registro(H1, recurso));
    }

    /**
     * Lo registrado dice cuándo se reservó la clave (#219): con eso el alta asistida deja de devolver la API key a la hora, aunque la
     * limpieza, que corre cada hora, todavía no haya borrado la respuesta.
     */
    @Test void loRegistradoDiceCuandoSeReservoLaClave() {
        Instant antes = Instant.now().minusSeconds(5);
        repo.reservar("alta-cuenta", "k1", H1);

        Instant creadoAt = repo.buscar("alta-cuenta", "k1").orElseThrow().creadoAt();

        assertThat(creadoAt).isNotNull().isBetween(antes, Instant.now().plusSeconds(5));
    }

    /** Buscar solo lee: una clave que no existe sigue sin existir (no la reserva), y la que existe se ve tal cual quedó. */
    @Test void buscarNoReservaNadaYDevuelveLoRegistrado() {
        UUID recurso = UUID.randomUUID();
        assertThat(repo.buscar("factura:t1", "k1")).isEmpty();
        assertThat(repo.reservar("factura:t1", "k1", H1)).as("buscar no dejó la clave tomada").isEmpty();
        repo.completar("factura:t1", "k1", recurso);

        assertThat(sinFecha(repo.buscar("factura:t1", "k1"))).contains(new Registro(H1, recurso));
        assertThat(repo.buscar("factura:t2", "k1")).as("otro alcance, otra clave").isEmpty();
    }

    /** Una reserva aún sin confirmar no se ve ni se espera: el reintento que llega en ese instante pasa a la reserva, que sí espera. */
    @Test void buscarNoVeNiEsperaUnaReservaSinConfirmar() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        CountDownLatch reservo = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> primero = pool.submit(() -> tx.executeWithoutResult(s -> {
            repo.reservar("factura:t1", "k1", H1);
            reservo.countDown();
            esperar(soltar);
        }));
        esperar(reservo);
        Future<Optional<Registro>> lectura = pool.submit(() -> repo.buscar("factura:t1", "k1"));

        assertThat(lectura.get(2, TimeUnit.SECONDS)).as("la lectura no queda esperando al primero").isEmpty();
        soltar.countDown();
        primero.get(5, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @Test void laMismaClaveEnOtroAlcanceEsOtraClave() {
        repo.reservar("factura:t1", "k1", H1);
        assertThat(repo.reservar("factura:t2", "k1", H1)).isEmpty();
    }

    @Test void completarUnaClaveNoTocaLaMismaClaveDeOtroAlcance() {
        UUID mio = UUID.randomUUID();
        repo.reservar("factura:t1", "k1", H1);
        repo.reservar("factura:t2", "k1", H2);

        repo.completar("factura:t1", "k1", mio);

        assertThat(sinFecha(repo.reservar("factura:t1", "k1", H1))).contains(new Registro(H1, mio));
        assertThat(sinFecha(repo.reservar("factura:t2", "k1", H2))).contains(new Registro(H2, null));
    }

    /** Si la operación se revierte, la reserva también: el reintento la vuelve a hacer. */
    @Test void unaReservaRevertidaDejaLaClaveLibre() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(s -> {
            assertThat(repo.reservar("factura:t1", "k1", H1)).isEmpty();
            s.setRollbackOnly();
        });
        assertThat(repo.reservar("factura:t1", "k1", H1)).isEmpty();
    }

    /**
     * Dos pedidos simultáneos con la misma clave: el segundo espera a que el primero confirme y ve lo que hizo, no reserva otra vez.
     * Es lo que impide dos facturas por un mismo reintento.
     */
    @Test void unPedidoSimultaneoConLaMismaClaveEsperaAlPrimeroYVeSuResultado() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        UUID recurso = UUID.randomUUID();
        CountDownLatch primeroReservo = new CountDownLatch(1);
        CountDownLatch soltarPrimero = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> primero = pool.submit(() -> tx.executeWithoutResult(s -> {
            assertThat(repo.reservar("factura:t1", "k1", H1)).isEmpty();
            primeroReservo.countDown();
            esperar(soltarPrimero);
            repo.completar("factura:t1", "k1", recurso);
        }));
        esperar(primeroReservo);
        Future<Optional<Registro>> segundo = pool.submit(() -> tx.execute(s -> repo.reservar("factura:t1", "k1", H1)));

        Thread.sleep(300);
        assertThat(segundo.isDone()).as("el segundo espera mientras el primero no confirma").isFalse();
        soltarPrimero.countDown();
        primero.get(10, TimeUnit.SECONDS);
        assertThat(sinFecha(segundo.get(10, TimeUnit.SECONDS))).contains(new Registro(H1, recurso));
        pool.shutdown();
    }

    @Test void siElPrimeroSeRevierteElQueEsperabaReservaEl() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        CountDownLatch primeroReservo = new CountDownLatch(1);
        CountDownLatch soltarPrimero = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> primero = pool.submit(() -> tx.executeWithoutResult(s -> {
            repo.reservar("factura:t1", "k1", H1);
            primeroReservo.countDown();
            esperar(soltarPrimero);
            s.setRollbackOnly();
        }));
        esperar(primeroReservo);
        Future<Optional<Registro>> segundo = pool.submit(() -> tx.execute(s -> repo.reservar("factura:t1", "k1", H1)));
        soltarPrimero.countDown();
        primero.get(10, TimeUnit.SECONDS);

        assertThat(segundo.get(10, TimeUnit.SECONDS)).as("la clave quedó libre y la reserva el segundo").isEmpty();
        pool.shutdown();
    }

    @Test void borraLasClavesAnterioresAlLimiteYNoLasDemas() {
        repo.reservar("factura:t1", "vieja", H1);
        repo.reservar("factura:t1", "nueva", H1);
        jdbc.update("UPDATE idempotencia SET creado_at = ? WHERE clave = 'vieja'", Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)));

        int borradas = repo.borrarAnterioresA(Instant.now().minus(24, ChronoUnit.HOURS));

        assertThat(borradas).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT clave FROM idempotencia", String.class)).containsExactly("nueva");
    }

    // --- #219: respuesta guardada ----------------------------------------------------------------------------------------------

    @Test void buscarNoReservaYDevuelveLaRespuestaGuardada() {
        assertThat(repo.buscar("alta-cuenta", "k1")).isEmpty();
        assertThat(repo.buscar("alta-cuenta", "k1")).as("buscar no reserva").isEmpty();

        UUID cuenta = UUID.randomUUID();
        repo.reservar("alta-cuenta", "k1", H1);
        repo.completar("alta-cuenta", "k1", cuenta, new byte[]{9, 8, 7});

        Registro r = repo.buscar("alta-cuenta", "k1").orElseThrow();
        assertThat(r.huella()).isEqualTo(H1);
        assertThat(r.recursoId()).isEqualTo(cuenta);
        assertThat(r.respuestaCifrada()).containsExactly(9, 8, 7);
        assertThat(repo.reservar("alta-cuenta", "k1", H1).orElseThrow().respuestaCifrada()).containsExactly(9, 8, 7);
    }

    /** La respuesta lleva un secreto: se olvida pronto, pero la clave sigue y un reintento tardío se reconoce. */
    @Test void olvidaLasRespuestasViejasYConservaLaClave() {
        repo.reservar("alta-cuenta", "vieja", H1);
        repo.completar("alta-cuenta", "vieja", UUID.randomUUID(), new byte[]{1});
        repo.reservar("alta-cuenta", "nueva", H1);
        repo.completar("alta-cuenta", "nueva", UUID.randomUUID(), new byte[]{2});
        jdbc.update("UPDATE idempotencia SET creado_at = ? WHERE clave = 'vieja'", Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));

        int olvidadas = repo.olvidarRespuestasAnterioresA(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(olvidadas).isEqualTo(1);
        assertThat(repo.buscar("alta-cuenta", "vieja").orElseThrow()).satisfies(r -> {
            assertThat(r.respuestaCifrada()).isNull();
            assertThat(r.huella()).isEqualTo(H1);
        });
        assertThat(repo.buscar("alta-cuenta", "nueva").orElseThrow().respuestaCifrada()).containsExactly(2);
    }

    private static void esperar(CountDownLatch l) {
        try {
            if (!l.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        } catch (InterruptedException e) { throw new IllegalStateException(e); }
    }
}
