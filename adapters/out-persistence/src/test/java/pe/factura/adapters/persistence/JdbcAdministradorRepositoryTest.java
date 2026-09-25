package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAdministradorRepositoryTest extends PersistenciaTestBase {
    JdbcAdministradorRepository repo = new JdbcAdministradorRepository(jdbc);

    @Test void guardaBuscaYActualiza() {
        Administrador a = new Administrador(UUID.randomUUID(), "Ana@Khipu.pe", "hash-1", true);
        repo.guardar(a);

        assertThat(repo.buscar(a.id())).get().extracting(Administrador::email).isEqualTo("ana@khipu.pe");
        assertThat(repo.buscarPorEmail("ana@khipu.pe")).get().extracting(Administrador::id).isEqualTo(a.id());
        assertThat(repo.buscarPorEmail("nadie@khipu.pe")).isEmpty();
        assertThat(repo.buscar(UUID.randomUUID())).isEmpty();

        repo.guardar(new Administrador(a.id(), a.email(), "hash-2", false));
        Administrador actualizado = repo.buscar(a.id()).orElseThrow();
        assertThat(actualizado.passwordHash()).isEqualTo("hash-2");
        assertThat(actualizado.activo()).isFalse();
    }
}
