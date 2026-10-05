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

    @Test void laPrimeraVezReservaYLaSegundaDevuelveLoRegistrado() {
        UUID recurso = UUID.randomUUID();
        assertThat(repo.reservar("factura:t1", "k1", H1)).isEmpty();
        repo.completar("factura:t1", "k1", recurso);

        assertThat(repo.reservar("factura:t1", "k1", H2)).contains(new Registro(H1, recurso));
    }

    /** Buscar solo lee: una clave que no existe sigue sin existir (no la reserva), y la que existe se ve tal cual quedó. */
    @Test void buscarNoReservaNadaYDevuelveLoRegistrado() {
        UUID recurso = UUID.randomUUID();
        assertThat(repo.buscar("factura:t1", "k1")).isEmpty();
        assertThat(repo.reservar("factura:t1", "k1", H1)).as("buscar no dejó la clave tomada").isEmpty();
        repo.completar("factura:t1", "k1", recurso);

        assertThat(repo.buscar("factura:t1", "k1")).contains(new Registro(H1, recurso));
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

        assertThat(repo.reservar("factura:t1", "k1", H1)).contains(new Registro(H1, mio));
        assertThat(repo.reservar("factura:t2", "k1", H2)).contains(new Registro(H2, null));
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
        assertThat(segundo.get(10, TimeUnit.SECONDS)).contains(new Registro(H1, recurso));
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

    private static void esperar(CountDownLatch l) {
        try {
            if (!l.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        } catch (InterruptedException e) { throw new IllegalStateException(e); }
    }
}
