package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.VigiladoRepository.EstadoAlerta;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** El vigilante de la operación (#341), con Postgres real: los conteos por estado y fecha de cambio, los latidos y el registro de alertas con su upsert. */
class JdbcVigiladoRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcVigiladoRepository repo = new JdbcVigiladoRepository(jdbc);

    @BeforeEach void limpiarVigilado() {
        jdbc.update("TRUNCATE alerta_operador, latido_operativo");
    }

    void documento(UUID tenant, String estado, Instant cambio) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, created_at, updated_at) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'x', ?, ?)",
                UUID.randomUUID(), tenant, NUMERO.getAndIncrement(), java.sql.Date.valueOf(LocalDate.of(2026, 10, 15)), estado, Timestamp.from(cambio.minusSeconds(3600)), Timestamp.from(cambio));
    }

    @Test void cuentaLosEnErrorDeEnvioQueLlevanAlMenosEseTiempoDeTodasLasEmpresas() {
        UUID a = tenantDePrueba(), b = tenantDePrueba();
        documento(a, "ERROR_ENVIO", AHORA.minusSeconds(31 * 60));
        documento(b, "ERROR_ENVIO", AHORA.minusSeconds(3 * 3600));
        documento(a, "ERROR_ENVIO", AHORA.minusSeconds(29 * 60));
        documento(a, "FIRMADO", AHORA.minusSeconds(3 * 3600));
        documento(b, "ACEPTADO", AHORA.minusSeconds(3 * 3600));

        assertThat(repo.enErrorDeEnvioDesde(AHORA.minusSeconds(30 * 60))).isEqualTo(2);
    }

    @Test void cuentaLosFueraDePlazoDesdeLaVentanaIncluyendoElBorde() {
        UUID a = tenantDePrueba();
        documento(a, "FUERA_DE_PLAZO", AHORA.minusSeconds(24 * 3600));
        documento(a, "FUERA_DE_PLAZO", AHORA.minusSeconds(24 * 3600 + 1));
        documento(a, "FUERA_DE_PLAZO", AHORA.minusSeconds(60));
        documento(a, "ERROR_ENVIO", AHORA.minusSeconds(60));

        assertThat(repo.fueraDePlazoDesde(AHORA.minusSeconds(24 * 3600))).isEqualTo(2);
    }

    @Test void elLatidoDeUnProcesoExternoSeLeePorClave() {
        assertThat(repo.ultimoLatido("respaldo")).isEmpty();

        jdbc.update("INSERT INTO latido_operativo (clave, ultimo) VALUES ('respaldo', ?)", Timestamp.from(AHORA));

        assertThat(repo.ultimoLatido("respaldo")).contains(AHORA);
        assertThat(repo.ultimoLatido("otro")).isEmpty();
    }

    @Test void guardarUnaAlertaLaCreaYLaVuelveAGuardarLaActualizaSinDuplicarla() {
        assertThat(repo.alertas()).isEmpty();

        repo.guardarAlerta("OUTBOX_ATASCADO", new EstadoAlerta(true, AHORA, AHORA));
        repo.guardarAlerta("CORREO_DESHABILITADO", new EstadoAlerta(true, AHORA, AHORA));
        repo.guardarAlerta("OUTBOX_ATASCADO", new EstadoAlerta(false, AHORA, AHORA.plusSeconds(600)));

        assertThat(repo.alertas()).hasSize(2)
                .containsEntry("OUTBOX_ATASCADO", new EstadoAlerta(false, AHORA, AHORA.plusSeconds(600)))
                .containsEntry("CORREO_DESHABILITADO", new EstadoAlerta(true, AHORA, AHORA));
    }
}
