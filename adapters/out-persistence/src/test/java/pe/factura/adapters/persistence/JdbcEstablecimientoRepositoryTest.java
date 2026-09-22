package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEstablecimientoRepositoryTest extends PersistenciaTestBase {
    JdbcEstablecimientoRepository repo = new JdbcEstablecimientoRepository(jdbc);

    @Test void guardaActualizaYListaPorEmpresa() {
        UUID t = tenantDePrueba();
        UUID otra = tenantDePrueba();
        repo.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", new Domicilio("150122", "Av. Larco 345", "Urb. Aurora", null, null, null, null), true));
        repo.guardar(new Establecimiento(t, "0001", "Almacén", Domicilio.de("150101", "Jr. Lampa 100"), true));
        repo.guardar(new Establecimiento(otra, "0001", "Ajena", Domicilio.de("150101", "Otra 1"), true));

        Establecimiento e = repo.buscar(t, "0002").orElseThrow();
        assertThat(e.nombre()).isEqualTo("Tienda Miraflores");
        assertThat(e.domicilio().codigoEstablecimiento()).isEqualTo("0002");
        assertThat(e.domicilio().urbanizacion()).isEqualTo("Urb. Aurora");
        assertThat(e.domicilio().distrito()).isEqualTo("MIRAFLORES");
        assertThat(repo.listar(t)).extracting(Establecimiento::codigo).containsExactly("0001", "0002");

        repo.guardar(e.con("Tienda Larco", e.domicilio(), false));   // mismo código: edición + baja lógica
        Establecimiento baja = repo.buscar(t, "0002").orElseThrow();
        assertThat(baja.nombre()).isEqualTo("Tienda Larco");
        assertThat(baja.activo()).isFalse();
        assertThat(repo.listar(t)).hasSize(2);
        assertThat(repo.buscar(otra, "0002")).isEmpty();
    }

    @Test void buscarConBloqueoDevuelveLoMismoQueBuscar() {
        UUID t = tenantDePrueba();
        repo.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", Domicilio.de("150122", "Av. Larco 345"), true));
        Establecimiento e = uow.ejecutar(() -> repo.buscarConBloqueo(t, "0002")).orElseThrow();
        assertThat(e.nombre()).isEqualTo("Tienda Miraflores");
        assertThat(uow.ejecutar(() -> repo.buscarConBloqueo(t, "0009"))).isEmpty();
    }

    /**
     * Prueba el lock en sí (no solo que lea bien): dos transacciones que hacen buscarConBloqueo sobre el mismo
     * establecimiento nunca están dentro de su sección crítica al mismo tiempo — así crearSerie y
     * desactivarEstablecimiento se serializan de verdad. Mismo patrón que JdbcSerieRepositoryTest.concurrenciaNoDuplicaNumeros.
     */
    @Test void buscarConBloqueoSerializaTransaccionesConcurrentesSobreLaMismaFila() throws Exception {
        UUID t = tenantDePrueba();
        repo.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", Domicilio.de("150122", "Av. Larco 345"), true));
        AtomicInteger dentro = new AtomicInteger(0);
        AtomicBoolean solapado = new AtomicBoolean(false);
        Runnable tarea = () -> uow.ejecutar(() -> {
            Establecimiento e = repo.buscarConBloqueo(t, "0002").orElseThrow();
            if (dentro.incrementAndGet() > 1) solapado.set(true);
            try { Thread.sleep(100); } catch (InterruptedException ignored) { } finally { dentro.decrementAndGet(); }
            repo.guardar(e);
        });
        ExecutorService ex = Executors.newFixedThreadPool(2);
        List<Future<?>> futuros = List.of(ex.submit(tarea), ex.submit(tarea));
        for (Future<?> f : futuros) f.get();
        ex.shutdown();
        assertThat(solapado).isFalse();
    }
}
