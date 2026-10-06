package pe.factura.adapters.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La búsqueda sin tildes (#214) en una base con {@code LC_CTYPE=C}, la configuración en la que {@code ILIKE} solo pasa a minúscula
 * el ASCII: ahí «PEÑA» no encontraba «Peña» ni «LIBRERÍA» encontraba «Librería» si la regla dependiera de la base. El resto de los
 * tests corre con la configuración por defecto de la imagen ({@code en_US.utf8}), que lo tapa; por eso esta clase tiene su propio
 * Postgres. No se sabe qué {@code LC_CTYPE} tiene el de producción: la búsqueda no debe depender de él.
 */
@Testcontainers
class JdbcCuentasAdminRepositoryLocaleCTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_INITDB_ARGS", "--locale=C --encoding=UTF8");
    static JdbcTemplate jdbc;
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcCuentasAdminRepository repo = new JdbcCuentasAdminRepository(jdbc);

    @BeforeAll static void migrar() {
        DriverManagerDataSource d = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(d);
        Flyway.configure().dataSource(d).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void limpiar() { jdbc.update("TRUNCATE tenant, cuenta CASCADE"); }

    /** Sin esto, los demás tests podrían pasar en una base que no es la que dicen probar. */
    @Test void laBaseDeEstaClaseTieneLcCtypeC() {
        assertThat(jdbc.queryForObject("SELECT datctype FROM pg_database WHERE datname = current_database()", String.class)).isEqualTo("C");
        assertThat(jdbc.queryForObject("SELECT 'Peña' ILIKE '%PEÑA%'", Boolean.class)).as("ILIKE solo, sin la regla, no basta en C").isFalse();
    }

    @Test void laEnieEnMayusculasYMinusculasSinDependerDeLaBase() {
        cuenta("Peña Hermanos", "hermanos@x.pe", T0);
        cuenta("Pena Sur", "sur@x.pe", T0.plusSeconds(1));
        UUID conEmpresa = cuenta("Mi negocio", "negocio@x.pe", T0.plusSeconds(2));
        empresa(conEmpresa, "20100066603", "COMERCIAL MUÑOZ SAC");

        assertThat(emails(new Filtro("PEÑA"))).containsExactly("hermanos@x.pe");
        assertThat(emails(new Filtro("peña"))).containsExactly("hermanos@x.pe");
        assertThat(emails(new Filtro("muñoz"))).containsExactly("negocio@x.pe");
        assertThat(emails(new Filtro("pena"))).as("la ñ sigue siendo otra letra").containsExactly("sur@x.pe");
    }

    @Test void lasVocalesConTildeEnMayusculasTampocoDependenDeLaBase() {
        cuenta("Librería El Saber", "libro@x.pe", T0);

        assertThat(emails(new Filtro("LIBRERÍA"))).containsExactly("libro@x.pe");
        assertThat(emails(new Filtro("LIBRERIA"))).containsExactly("libro@x.pe");
    }

    private UUID cuenta(String nombre, String email, Instant creada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, ?, ?, '987654321', ?)", id, nombre, email, Timestamp.from(creada));
        return id;
    }

    private void empresa(UUID cuenta, String ruc, String razonSocial) {
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, ?, 'BETA', ?)", UUID.randomUUID(), ruc, razonSocial, cuenta);
    }

    private List<String> emails(Filtro filtro) {
        return repo.listar(filtro, 1, 20).stream().map(CuentaResumen::email).toList();
    }
}
