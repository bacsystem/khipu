package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.EstablecimientoRepository.Asignacion;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;

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

    /**
     * buscarAsignacionDeSerie es el JOIN que reemplaza los 2 SELECTs separados (serie + establecimiento) del camino
     * caliente de emisión: cubre serie en 0000, serie en un anexo activo, serie en un anexo inexistente y serie inexistente.
     */
    @Test void buscarAsignacionDeSerieResuelveLosCuatroCasos() {
        UUID t = tenantDePrueba();
        JdbcSerieRepository series = new JdbcSerieRepository(jdbc);
        repo.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", Domicilio.de("150122", "Av. Larco 345"), true));
        series.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 0, true));
        series.crear(new Serie(t, TipoDocumento.FACTURA, "F002", 0, true, "0002"));
        series.crear(new Serie(t, TipoDocumento.FACTURA, "F003", 0, true, "0009"));   // apunta a un anexo que nunca se registró

        assertThat(repo.buscarAsignacionDeSerie(t, TipoDocumento.FACTURA, "F001")).isEmpty();   // 0000: usa el domicilio fiscal
        assertThat(repo.buscarAsignacionDeSerie(t, TipoDocumento.FACTURA, "F009")).isEmpty();   // serie inexistente

        Asignacion asignada = repo.buscarAsignacionDeSerie(t, TipoDocumento.FACTURA, "F002").orElseThrow();
        assertThat(asignada.codigo()).isEqualTo("0002");
        assertThat(asignada.establecimiento().nombre()).isEqualTo("Tienda Miraflores");

        Asignacion huerfana = repo.buscarAsignacionDeSerie(t, TipoDocumento.FACTURA, "F003").orElseThrow();
        assertThat(huerfana.codigo()).isEqualTo("0009");
        assertThat(huerfana.establecimiento()).isNull();
    }
}
