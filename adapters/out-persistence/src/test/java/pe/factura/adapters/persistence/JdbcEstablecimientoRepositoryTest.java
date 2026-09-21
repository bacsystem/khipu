package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

import java.util.UUID;

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

    /** buscarConBloqueo es lo que serializa crearSerie con desactivarEstablecimiento (fila bloqueada dentro de la transacción). */
    @Test void buscarConBloqueoDevuelveLoMismoQueBuscar() {
        UUID t = tenantDePrueba();
        repo.guardar(new Establecimiento(t, "0002", "Tienda Miraflores", Domicilio.de("150122", "Av. Larco 345"), true));
        Establecimiento e = uow.ejecutar(() -> repo.buscarConBloqueo(t, "0002")).orElseThrow();
        assertThat(e.nombre()).isEqualTo("Tienda Miraflores");
        assertThat(uow.ejecutar(() -> repo.buscarConBloqueo(t, "0009"))).isEmpty();
    }
}
