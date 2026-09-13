package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class JdbcSerieRepositoryTest extends PersistenciaTestBase {
    JdbcSerieRepository repo = new JdbcSerieRepository(jdbc);

    @Test void siguienteNumeroEsCorrelativo() {
        UUID t = tenantDePrueba();
        repo.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 10, true));
        assertThat(uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))).isEqualTo(11);
        assertThat(uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))).isEqualTo(12);
    }

    @Test void serieNoConfigurada() {
        UUID t = tenantDePrueba();
        assertThatThrownBy(() -> uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F009")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }

    @Test void concurrenciaNoDuplicaNumeros() throws Exception {
        UUID t = tenantDePrueba();
        repo.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 0, true));
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<Long>> futuros = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) futuros.add(ex.submit(() -> uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))));
        java.util.Set<Long> numeros = new java.util.HashSet<>();
        for (Future<Long> f : futuros) numeros.add(f.get());
        ex.shutdown();
        assertThat(numeros).hasSize(40).contains(1L, 40L);
    }

    @Test void avanzarHastaElevaYNuncaReduce() {
        UUID t = tenantDePrueba();
        repo.crear(new Serie(t, TipoDocumento.FACTURA, "F001", 10, true));
        uow.ejecutar(() -> repo.avanzarHasta(t, TipoDocumento.FACTURA, "F001", 50));
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND codigo = 'F001'", Long.class, t)).isEqualTo(50L);
        uow.ejecutar(() -> repo.avanzarHasta(t, TipoDocumento.FACTURA, "F001", 20));
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND codigo = 'F001'", Long.class, t)).isEqualTo(50L);
        assertThat(uow.ejecutar(() -> repo.siguienteNumero(t, TipoDocumento.FACTURA, "F001"))).isEqualTo(51);
    }

    @Test void avanzarHastaEnSerieNoConfiguradaLanza() {
        UUID t = tenantDePrueba();
        assertThatThrownBy(() -> uow.ejecutar(() -> repo.avanzarHasta(t, TipoDocumento.FACTURA, "F009", 5)))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SERIE_NO_CONFIGURADA");
    }
}
