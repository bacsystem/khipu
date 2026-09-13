package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.OutboxItem;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
