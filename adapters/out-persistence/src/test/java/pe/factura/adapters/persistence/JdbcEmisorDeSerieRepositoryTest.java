package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.EmisorDeSerieRepository.Asignacion;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;
import pe.factura.domain.tenant.Serie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * buscarAsignacionDeSerie es el JOIN que reemplaza los 2 SELECTs separados (serie + establecimiento) del camino
 * caliente de emisión: cubre serie en 0000, serie en un anexo activo, serie en un anexo inexistente y serie inexistente.
 */
class JdbcEmisorDeSerieRepositoryTest extends PersistenciaTestBase {
    JdbcEmisorDeSerieRepository repo = new JdbcEmisorDeSerieRepository(jdbc);
    JdbcEstablecimientoRepository establecimientos = new JdbcEstablecimientoRepository(jdbc);
    JdbcSerieRepository series = new JdbcSerieRepository(jdbc);

    @Test void buscarAsignacionDeSerieResuelveLosCuatroCasos() {
        UUID t = tenantDePrueba();
        establecimientos.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", Domicilio.de("150122", "Av. Larco 345"), true));
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
