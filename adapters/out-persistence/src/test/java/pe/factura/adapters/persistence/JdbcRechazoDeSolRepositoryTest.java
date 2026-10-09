package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.OutboxItem;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** #107: con las credenciales SOL rechazadas, el outbox deja de tomar los envíos de esa empresa hasta que se corrigen. */
class JdbcRechazoDeSolRepositoryTest extends PersistenciaTestBase {
    final JdbcRechazoDeSolRepository rechazos = new JdbcRechazoDeSolRepository(jdbc);
    final JdbcOutboxRepository outbox = new JdbcOutboxRepository(jdbc);
    final Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    List<UUID> tomadas() {
        return uow.ejecutar(() -> outbox.tomarVencidas(50, Duration.ofSeconds(1))).stream().map(OutboxItem::tenantId).toList();
    }

    @Test void marcaYConservaLaFechaDelPrimerRechazo() {
        UUID t = tenantDePrueba();
        rechazos.marcar(t, "0102 - Usuario o contrasena incorrectos", t0);
        rechazos.marcar(t, "0104 - La Clave ingresada es incorrecta", t0.plusSeconds(60));

        var r = rechazos.buscar(t).orElseThrow();
        assertThat(r.en()).isEqualTo(t0);
        assertThat(r.motivo()).isEqualTo("0104 - La Clave ingresada es incorrecta");
        assertThat(rechazos.buscar(tenantDePrueba())).isEmpty();
    }

    @Test void elOutboxNoTomaLosEnviosDeUnaEmpresaConCredencialesRechazadas() {
        UUID rechazada = tenantDePrueba(), sana = tenantDePrueba();
        outbox.programar(rechazada, "ENVIAR", UUID.randomUUID(), t0.minusSeconds(5));
        outbox.programar(sana, "ENVIAR", UUID.randomUUID(), t0.minusSeconds(5));
        rechazos.marcar(rechazada, "0102 - Usuario o contrasena incorrectos", t0);

        assertThat(tomadas()).containsExactly(sana);
    }

    @Test void levantarElRechazoAdelantaLosEnviosQueEsperaban() {
        UUID t = tenantDePrueba();
        // Uno que ya había fallado y esperaba su reintento dentro de 6 horas.
        outbox.programar(t, "ENVIAR", UUID.randomUUID(), t0.plus(6, ChronoUnit.HOURS));
        rechazos.marcar(t, "0102 - Usuario o contrasena incorrectos", t0);

        assertThat(rechazos.levantar(t, t0)).isTrue();

        assertThat(rechazos.buscar(t)).isEmpty();
        assertThat(tomadas()).as("se reintenta ya, no en 6 horas").containsExactly(t);
        assertThat(rechazos.levantar(t, t0)).as("ya no estaba marcada").isFalse();
    }

    @Test void levantarSinRechazoNoAdelantaNada() {
        UUID t = tenantDePrueba();
        outbox.programar(t, "ENVIAR", UUID.randomUUID(), t0.plus(6, ChronoUnit.HOURS));

        assertThat(rechazos.levantar(t, t0)).isFalse();
        assertThat(tomadas()).isEmpty();
    }
}
