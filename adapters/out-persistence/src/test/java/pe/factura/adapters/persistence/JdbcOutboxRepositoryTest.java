package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.OutboxItem;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcOutboxRepositoryTest extends PersistenciaTestBase {
    JdbcOutboxRepository repo = new JdbcOutboxRepository(jdbc);

    @Test void tomaSoloVencidasYLasBloquea() {
        UUID t = tenantDePrueba(), doc = UUID.randomUUID();
        repo.programar(t, "ENVIAR", doc, Instant.now().minusSeconds(5));
        repo.programar(t, "ENVIAR", UUID.randomUUID(), Instant.now().plusSeconds(3600));
        List<OutboxItem> tomadas = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)));
        assertThat(tomadas).hasSize(1);
        assertThat(tomadas.get(0).agregadoId()).isEqualTo(doc);
        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).isEmpty();   // bloqueada
    }

    @Test void reprogramarYCompletar() {
        UUID t = tenantDePrueba();
        repo.programar(t, "ENVIAR", UUID.randomUUID(), Instant.now().minusSeconds(5));
        OutboxItem item = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).get(0);
        repo.reprogramar(item.id(), Instant.now().minusSeconds(1), "timeout");
        OutboxItem otraVez = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).get(0);
        assertThat(otraVez.intentos()).isEqualTo(1);
        repo.completar(otraVez.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isZero();
    }

    @Test void tomarVencidasFueraDeTransaccionLanza() {
        UUID t = tenantDePrueba();
        repo.programar(t, "ENVIAR", UUID.randomUUID(), Instant.now().minusSeconds(5));
        assertThatThrownBy(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void programarEsIdempotentePorAgregadoYAccion() {
        UUID t = tenantDePrueba(), doc = UUID.randomUUID();
        Instant primero = Instant.now().minusSeconds(5);
        repo.programar(t, "ENVIAR", doc, primero);
        repo.programar(t, "ENVIAR", doc, Instant.now().plusSeconds(3600));   // no-op: conserva la fila original
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?", Integer.class, doc)).isEqualTo(1);
        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).hasSize(1);
        // Otra acción sobre el mismo agregado sí crea su propia fila
        repo.programar(t, "OTRA", doc, primero);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?", Integer.class, doc)).isEqualTo(2);
    }

    @Test void programarTrasCompletarVuelveACrearLaFila() {
        UUID t = tenantDePrueba(), doc = UUID.randomUUID();
        repo.programar(t, "ENVIAR", doc, Instant.now().minusSeconds(5));
        OutboxItem item = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).get(0);
        repo.completar(item.id());
        repo.programar(t, "ENVIAR", doc, Instant.now().minusSeconds(5));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?", Integer.class, doc)).isEqualTo(1);
    }
}
