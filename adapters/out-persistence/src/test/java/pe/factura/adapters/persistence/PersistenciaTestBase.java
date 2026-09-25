package pe.factura.adapters.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Testcontainers
abstract class PersistenciaTestBase {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource ds; static JdbcTemplate jdbc; static JdbcUnitOfWork uow;
    private static final AtomicLong RUC_SEQ = new AtomicLong(1);

    @BeforeAll static void migrar() {
        DriverManagerDataSource d = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        ds = d; jdbc = new JdbcTemplate(d); uow = new JdbcUnitOfWork(new DataSourceTransactionManager(d));
        Flyway.configure().dataSource(d).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, establecimiento, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador CASCADE");
    }

    static UUID tenantDePrueba() {
        UUID id = UUID.randomUUID();
        String ruc = "20" + String.format("%09d", RUC_SEQ.getAndIncrement());
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, ?, 'EMPRESA SAC', 'BETA')", id, ruc);
        return id;
    }
}
