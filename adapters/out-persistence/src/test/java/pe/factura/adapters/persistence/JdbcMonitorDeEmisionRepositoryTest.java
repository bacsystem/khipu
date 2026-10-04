package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Cola;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Conteo;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El monitor global de emisión (#195), con Postgres real: los comprobantes de **todas** las empresas agrupados por hora y estado, con los bordes del rango, y la cola del
 * outbox con su definición de «vencido» (la del trabajo que la vacía).
 */
class JdbcMonitorDeEmisionRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-15T15:20:00Z");
    static final AtomicLong NUMERO = new AtomicLong(1);

    JdbcMonitorDeEmisionRepository repo = new JdbcMonitorDeEmisionRepository(jdbc);

    void documento(UUID tenant, EstadoDocumento estado, Instant creado) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, created_at) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'x', ?)",
                UUID.randomUUID(), tenant, NUMERO.getAndIncrement(), java.sql.Date.valueOf(LocalDate.of(2026, 10, 15)), estado.name(), Timestamp.from(creado));
    }

    void outbox(UUID tenant, Instant creado, Instant siguiente, Instant bloqueadoHasta) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento, locked_until, created_at) VALUES (?, ?, 'ENVIAR', ?, ?, ?)",
                tenant, UUID.randomUUID(), Timestamp.from(siguiente), bloqueadoHasta == null ? null : Timestamp.from(bloqueadoHasta), Timestamp.from(creado));
    }

    Instant hace(Duration d) { return AHORA.minus(d); }

    // --- por hora y estado ------------------------------------------------------------------------------------------------------------------

    @Test void agrupaPorHoraYEstadoConElInicioDeLaHora() {
        UUID t = tenantDePrueba();
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T14:05:00Z"));
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T14:59:59Z"));
        documento(t, EstadoDocumento.RECHAZADO, Instant.parse("2026-10-15T14:30:00Z"));
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T15:00:00Z"));

        List<Conteo> r = repo.porHoraYEstado(hace(Duration.ofHours(24)));

        assertThat(r).containsExactlyInAnyOrder(
                new Conteo(Instant.parse("2026-10-15T14:00:00Z"), EstadoDocumento.ACEPTADO, 2),
                new Conteo(Instant.parse("2026-10-15T14:00:00Z"), EstadoDocumento.RECHAZADO, 1),
                new Conteo(Instant.parse("2026-10-15T15:00:00Z"), EstadoDocumento.ACEPTADO, 1));
    }

    @Test void sumaLasEmpresasEnLugarDeSepararlas() {
        UUID a = tenantDePrueba();
        UUID b = tenantDePrueba();
        documento(a, EstadoDocumento.ACEPTADO, hace(Duration.ofMinutes(10)));
        documento(b, EstadoDocumento.ACEPTADO, hace(Duration.ofMinutes(5)));

        assertThat(repo.porHoraYEstado(hace(Duration.ofHours(1)))).extracting(Conteo::cantidad).containsExactly(2L);
    }

    @Test void elBordeDelRangoEsInclusivoYLoAnteriorNoEntra() {
        UUID t = tenantDePrueba();
        Instant desde = Instant.parse("2026-10-14T15:00:00Z");
        documento(t, EstadoDocumento.ACEPTADO, desde.minusSeconds(1));
        documento(t, EstadoDocumento.RECHAZADO, desde);
        documento(t, EstadoDocumento.ENVIADO, desde.plusSeconds(1));

        List<Conteo> r = repo.porHoraYEstado(desde);

        assertThat(r).extracting(Conteo::estado).containsExactlyInAnyOrder(EstadoDocumento.RECHAZADO, EstadoDocumento.ENVIADO);
    }

    @Test void unComprobanteAntesDelRangoNoApareceAunqueSeaDeLaMismaHora() {
        UUID t = tenantDePrueba();
        // La hora de 14:00 empieza antes del rango (14:30): solo cuenta lo que se creó desde las 14:30.
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T14:10:00Z"));
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T14:40:00Z"));

        assertThat(repo.porHoraYEstado(Instant.parse("2026-10-15T14:30:00Z"))).containsExactly(new Conteo(Instant.parse("2026-10-15T14:00:00Z"), EstadoDocumento.ACEPTADO, 1));
    }

    @Test void sinComprobantesRecientesNoTraeNada() {
        UUID t = tenantDePrueba();
        documento(t, EstadoDocumento.ACEPTADO, hace(Duration.ofDays(3)));

        assertThat(repo.porHoraYEstado(hace(Duration.ofHours(24)))).isEmpty();
    }

    @Test void trae_todosLosEstadosQueSeLeDan() {
        UUID t = tenantDePrueba();
        for (EstadoDocumento e : EstadoDocumento.values()) documento(t, e, hace(Duration.ofMinutes(1)));

        assertThat(repo.porHoraYEstado(hace(Duration.ofHours(1)))).extracting(Conteo::estado).containsExactlyInAnyOrder(EstadoDocumento.values());
    }

    /** Una zona con media hora de desfase: si la hora se agrupara en la zona de la sesión, 14:05 UTC caería en la hora de las 13:30 UTC. */
    @Test void laHoraNoDependeDeLaZonaDeLaSesionDeLaBase() {
        UUID t = tenantDePrueba();
        documento(t, EstadoDocumento.ACEPTADO, Instant.parse("2026-10-15T14:05:00Z"));
        // Una sola conexión: con el `DriverManagerDataSource` de la base cada consulta abre la suya, y el `SET` no llegaría a la del repositorio.
        try (SingleConnectionDataSource unica = new SingleConnectionDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword(), true)) {
            JdbcTemplate sesion = new JdbcTemplate(unica);
            sesion.execute("SET TIME ZONE 'Asia/Kolkata'");
            assertThat(sesion.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("Asia/Kolkata");

            assertThat(new JdbcMonitorDeEmisionRepository(sesion).porHoraYEstado(hace(Duration.ofHours(24)))).extracting(Conteo::hora).containsExactly(Instant.parse("2026-10-15T14:00:00Z"));
        }
    }

    @Test void hayUnIndiceSobreLaFechaDeCreacionParaNoRecorrerTodaLaTabla() {
        Integer indices = jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = 'documento' AND indexdef LIKE '%(created_at)%'", Integer.class);

        assertThat(indices).isEqualTo(1);
    }

    // --- la cola del outbox -----------------------------------------------------------------------------------------------------------------

    @Test void unaColaVaciaTraeCerosYSinFechas() {
        Cola c = repo.cola(AHORA);

        assertThat(c.pendientes()).isZero();
        assertThat(c.vencidos()).isZero();
        assertThat(c.masViejoDesde()).isNull();
        assertThat(c.vencidoDesde()).isNull();
    }

    @Test void cuentaLosPendientesYElMasViejoPorFechaDeProgramacion() {
        UUID t = tenantDePrueba();
        outbox(t, hace(Duration.ofHours(5)), hace(Duration.ofMinutes(1)), null);
        outbox(t, hace(Duration.ofHours(1)), hace(Duration.ofMinutes(2)), null);
        outbox(t, hace(Duration.ofMinutes(3)), AHORA.plus(Duration.ofHours(2)), null);

        Cola c = repo.cola(AHORA);

        assertThat(c.pendientes()).isEqualTo(3);
        assertThat(c.masViejoDesde()).isEqualTo(hace(Duration.ofHours(5)));
    }

    @Test void vencidoEsLoQueYaTocabaYNadieTomo() {
        UUID t = tenantDePrueba();
        outbox(t, hace(Duration.ofHours(2)), hace(Duration.ofMinutes(30)), null);
        outbox(t, hace(Duration.ofHours(2)), hace(Duration.ofMinutes(10)), hace(Duration.ofMinutes(1)));
        outbox(t, hace(Duration.ofHours(2)), AHORA.plus(Duration.ofMinutes(5)), null);
        outbox(t, hace(Duration.ofHours(2)), hace(Duration.ofMinutes(5)), AHORA.plus(Duration.ofMinutes(5)));

        Cola c = repo.cola(AHORA);

        assertThat(c.pendientes()).isEqualTo(4);
        assertThat(c.vencidos()).as("el de hace 30 min y el de hace 10 min con el bloqueo ya vencido").isEqualTo(2);
        assertThat(c.vencidoDesde()).isEqualTo(hace(Duration.ofMinutes(30)));
    }

    @Test void loQueEstaEnProcesoOEsperaSuReintentoNoEstaVencido() {
        UUID t = tenantDePrueba();
        outbox(t, hace(Duration.ofHours(1)), hace(Duration.ofMinutes(5)), AHORA.plus(Duration.ofMinutes(2)));
        outbox(t, hace(Duration.ofHours(1)), AHORA.plus(Duration.ofHours(3)), null);

        Cola c = repo.cola(AHORA);

        assertThat(c.pendientes()).isEqualTo(2);
        assertThat(c.vencidos()).isZero();
        assertThat(c.vencidoDesde()).isNull();
    }

    @Test void elBordeDeVencidoEsElInstanteExacto() {
        UUID t = tenantDePrueba();
        outbox(t, hace(Duration.ofHours(1)), AHORA, null);
        outbox(t, hace(Duration.ofHours(1)), AHORA.plusSeconds(1), null);

        assertThat(repo.cola(AHORA).vencidos()).isEqualTo(1);
    }

    @Test void unBloqueoQueVenceJustoAhoraYaNoBloquea() {
        UUID t = tenantDePrueba();
        outbox(t, hace(Duration.ofHours(1)), hace(Duration.ofMinutes(5)), AHORA);

        assertThat(repo.cola(AHORA).vencidos()).as("`locked_until < ahora` es estricto: el instante exacto todavía bloquea").isZero();
        assertThat(repo.cola(AHORA.plusSeconds(1)).vencidos()).isEqualTo(1);
    }

    @Test void laColaMezclaTodasLasEmpresas() {
        outbox(tenantDePrueba(), hace(Duration.ofHours(1)), hace(Duration.ofMinutes(5)), null);
        outbox(tenantDePrueba(), hace(Duration.ofHours(2)), hace(Duration.ofMinutes(9)), null);

        Cola c = repo.cola(AHORA);

        assertThat(c.pendientes()).isEqualTo(2);
        assertThat(c.vencidos()).isEqualTo(2);
        assertThat(c.vencidoDesde()).isEqualTo(hace(Duration.ofMinutes(9)));
    }
}
