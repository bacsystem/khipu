package pe.factura.adapters.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.domain.cuenta.AceptacionDeTerminos;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAceptacionDeTerminosRepositoryTest extends PersistenciaTestBase {
    final JdbcAceptacionDeTerminosRepository repo = new JdbcAceptacionDeTerminosRepository(jdbc);
    static final Instant AHORA = Instant.parse("2026-10-20T15:00:00Z");
    UUID usuario;

    @BeforeEach void unUsuario() {
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), "Mi negocio", "ana@negocio.pe", "987654321");
        new JdbcCuentaRepository(jdbc).guardar(cuenta);
        usuario = UUID.randomUUID();
        new JdbcUsuarioRepository(jdbc).guardar(new Usuario(usuario, cuenta.id(), "ana@negocio.pe", "hash", Rol.ADMIN, true));
    }

    /** Un usuario que ya existía antes de V64 no tiene ninguna fila: queda pendiente. */
    @Test void sinFilaNoHayAceptacion() {
        assertThat(repo.buscar(usuario, "2026-10-20")).isEmpty();
    }

    @Test void guardaLaVersionYLaFecha() {
        repo.registrar(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA));

        assertThat(repo.buscar(usuario, "2026-10-20")).contains(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA));
        assertThat(repo.buscar(usuario, "2027-01-15")).as("otra versión").isEmpty();
    }

    @Test void aceptarOtraVezConservaLaPrimeraFecha() {
        repo.registrar(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA));
        repo.registrar(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA.plus(3, ChronoUnit.DAYS)));

        assertThat(repo.buscar(usuario, "2026-10-20")).get().extracting(AceptacionDeTerminos::aceptadaEn).isEqualTo(AHORA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceptacion_terminos WHERE usuario_id = ?", Integer.class, usuario)).isEqualTo(1);
    }

    /** Una versión nueva se suma: la constancia de la anterior queda. */
    @Test void unaVersionNuevaSeSumaALaAnterior() {
        repo.registrar(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA));
        repo.registrar(new AceptacionDeTerminos(usuario, "2027-01-15", AHORA.plus(90, ChronoUnit.DAYS)));

        assertThat(repo.buscar(usuario, "2026-10-20")).isPresent();
        assertThat(repo.buscar(usuario, "2027-01-15")).get().extracting(AceptacionDeTerminos::aceptadaEn).isEqualTo(AHORA.plus(90, ChronoUnit.DAYS));
    }

    @Test void lasAceptacionesDeOtroUsuarioNoCuentan() {
        repo.registrar(new AceptacionDeTerminos(usuario, "2026-10-20", AHORA));

        assertThat(repo.buscar(UUID.randomUUID(), "2026-10-20")).isEmpty();
    }
}
