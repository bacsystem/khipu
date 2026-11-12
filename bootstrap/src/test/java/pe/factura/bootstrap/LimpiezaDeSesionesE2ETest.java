package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.factura.application.port.in.PurgarSesionesUseCase;
import pe.factura.application.service.TokenOpaco;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #611 de extremo a extremo: borrar sesiones vencidas no cambia ninguna respuesta de la API. Un refresh rotado que vuelve fuera de la gracia es
 * {@code 401 SESION_INVALIDA} con su fila presente y también con la fila ya borrada; lo vigente y lo que está en gracia no se toca; y el logout cierra
 * la familia aunque se haya borrado la fila del login.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class LimpiezaDeSesionesE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PurgarSesionesUseCase purga;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private String registrar() {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}", json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return refreshDe(r);
    }

    private ResponseEntity<Map> refrescar(String refresh) {
        return http.postForEntity("/v1/auth/refresh", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(refresh), json()), Map.class);
    }

    private String refreshDe(ResponseEntity<Map> r) { return (String) ((Map<?, ?>) r.getBody().get("datos")).get("refresh"); }

    private UUID idDe(String refresh) {
        return jdbc.queryForObject("SELECT id FROM sesion WHERE refresh_hash = ?", UUID.class, TokenOpaco.hash(refresh));
    }

    private boolean existe(UUID sesion) { return jdbc.queryForObject("SELECT count(*) FROM sesion WHERE id = ?", Integer.class, sesion) == 1; }

    /** Hace que la sesión haya vencido hace {@code dias} (y nazca 30 antes), como si el refresh hubiera dormido en una cookie. */
    private void envejecer(UUID sesion, int dias) {
        jdbc.update("UPDATE sesion SET expira_en = now() - make_interval(days => ?), created_at = now() - make_interval(days => ?) WHERE id = ?",
                dias, dias + 30, sesion);
    }

    private void assertSesionInvalida(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).containsEntry("codigo", "SESION_INVALIDA");
    }

    @Test void unRefreshRotadoFueraDeLaGraciaSigueDando401AntesYDespuesDeLaLimpieza() {
        String a = registrar();
        String b = refreshDe(refrescar(a));
        UUID filaA = idDe(a);
        jdbc.update("UPDATE sesion SET rotada_en = now() - interval '31 seconds' WHERE id = ?", filaA);

        assertSesionInvalida(refrescar(a));
        assertThat(purga.purgar()).as("no venció: la limpieza no la toca").isZero();
        assertThat(existe(filaA)).isTrue();
        assertSesionInvalida(refrescar(a));

        envejecer(filaA, 3);
        assertThat(purga.purgar()).as("vencida pero dentro de los 7 días de retención").isZero();
        assertThat(existe(filaA)).isTrue();
        assertSesionInvalida(refrescar(a));
        envejecer(filaA, 8);
        assertThat(purga.purgar()).isEqualTo(1);
        assertThat(existe(filaA)).as("vencida hace más que la retención: se borra").isFalse();
        assertSesionInvalida(refrescar(a));   // sin fila, la misma respuesta

        assertThat(refrescar(b).getStatusCode()).as("la sesión vigente no se tocó").isEqualTo(HttpStatus.OK);
    }

    @Test void elRefreshEnGraciaSigueSirviendoDespuesDeLaLimpieza() {
        String a = registrar();
        refrescar(a);

        assertThat(purga.purgar()).isZero();

        assertThat(refrescar(a).getStatusCode()).as("dentro de los 30 s la gracia sigue").isEqualTo(HttpStatus.OK);
    }

    @Test void elLogoutCierraLaFamiliaAunqueSeHayaBorradoLaFilaDelLogin() {
        String a = registrar();
        ResponseEntity<Map> renovada = refrescar(a);
        String b = refreshDe(renovada);
        UUID filaA = idDe(a);
        jdbc.update("UPDATE sesion SET rotada_en = now() - interval '31 seconds' WHERE id = ?", filaA);
        envejecer(filaA, 20);
        assertThat(purga.purgar()).isEqualTo(1);
        assertThat(existe(filaA)).isFalse();
        HttpHeaders conSesion = json();
        conSesion.setBearerAuth((String) ((Map<?, ?>) renovada.getBody().get("datos")).get("access"));

        assertThat(http.postForEntity("/v1/auth/logout", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(b), conSesion), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertSesionInvalida(refrescar(b));
    }

    @Test void laUltimaSesionDelUsuarioSeConservaAunqueEsteVencida() {
        String a = registrar();
        UUID unica = idDe(a);
        envejecer(unica, 40);

        assertThat(purga.purgar()).isZero();

        assertThat(existe(unica)).as("de ella sale el último acceso del backoffice").isTrue();
    }
}
