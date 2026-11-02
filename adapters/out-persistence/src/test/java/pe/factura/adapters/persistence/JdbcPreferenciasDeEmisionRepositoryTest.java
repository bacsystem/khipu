package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Las preferencias de emisión en su columna de {@code tenant} (V72) con Postgres real. */
class JdbcPreferenciasDeEmisionRepositoryTest extends PersistenciaTestBase {
    JdbcPreferenciasDeEmisionRepository repo = new JdbcPreferenciasDeEmisionRepository(jdbc);

    @Test void unaEmpresaNuevaLasTieneApagadas() {
        assertThat(repo.opcionesAvanzadas(tenantDePrueba())).isFalse();
    }

    @Test void seEncienden_seApagan_ySoloEnEsaEmpresa() {
        UUID t = tenantDePrueba();
        UUID otra = tenantDePrueba();

        assertThat(repo.fijarOpcionesAvanzadas(t, true)).isTrue();
        assertThat(repo.opcionesAvanzadas(t)).isTrue();
        assertThat(repo.opcionesAvanzadas(otra)).isFalse();

        assertThat(repo.fijarOpcionesAvanzadas(t, false)).isTrue();
        assertThat(repo.opcionesAvanzadas(t)).isFalse();
    }

    @Test void noTocaElRestoDeLaEmpresa() {
        UUID t = tenantDePrueba();
        String antes = jdbc.queryForObject("SELECT razon_social || '|' || entorno || '|' || envio_automatico_al_cliente FROM tenant WHERE id = ?", String.class, t);
        repo.fijarOpcionesAvanzadas(t, true);
        assertThat(jdbc.queryForObject("SELECT razon_social || '|' || entorno || '|' || envio_automatico_al_cliente FROM tenant WHERE id = ?", String.class, t)).isEqualTo(antes);
    }

    @Test void unaEmpresaQueNoExisteNoSeEscribeYSeLeeApagada() {
        UUID inexistente = UUID.randomUUID();
        assertThat(repo.fijarOpcionesAvanzadas(inexistente, true)).isFalse();
        assertThat(repo.opcionesAvanzadas(inexistente)).isFalse();
    }
}
