package pe.factura.adapters.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Las cuentas existentes migran a Gratis» (#189): se prueba migrando de verdad. La base se deja en la versión anterior a los planes, se crean cuentas como
 * existían entonces y recién después se aplica la migración. Con su propia base (no la compartida) porque necesita detenerse a mitad de camino.
 */
class MigracionDePlanesTest {
    static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Test void lasCuentasQueYaExistenMigranAGratisDesdeElDiaQueSeCrearon() {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            DriverManagerDataSource ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("36").load().migrate();
            UUID ana = UUID.randomUUID();
            UUID beto = UUID.randomUUID();
            jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Ana', 'ana@negocio.pe', '987654321', ?)", ana, Timestamp.from(T0));
            jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Beto', 'beto@negocio.pe', '987654322', ?)", beto, Timestamp.from(T0.plusSeconds(3600)));

            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

            List<Map<String, Object>> filas = jdbc.queryForList("""
                    SELECT s.cuenta_id, p.nombre, s.inicia_en, s.vence_en, s.termina_en FROM suscripcion s JOIN plan p ON p.id = s.plan_id ORDER BY s.inicia_en""");
            assertThat(filas).hasSize(2);
            assertThat(filas).extracting(f -> f.get("nombre")).containsOnly("Gratis");
            assertThat(filas).extracting(f -> f.get("cuenta_id")).containsExactly(ana, beto);
            assertThat(filas).extracting(f -> ((Timestamp) f.get("inicia_en")).toInstant()).containsExactly(T0, T0.plusSeconds(3600));
            assertThat(filas).extracting(f -> f.get("vence_en")).containsOnlyNulls();
            assertThat(filas).extracting(f -> f.get("termina_en")).containsOnlyNulls();
        }
    }

    @Test void sinCuentasLaMigracionNoCreaSuscripciones() {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            DriverManagerDataSource ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM suscripcion", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM plan", Integer.class)).isEqualTo(4);
        }
    }
}
