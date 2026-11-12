package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.factura.application.service.TokenOpaco;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #635 de extremo a extremo: presentar un refresh que ya se usó, pasada la gracia, revoca toda la familia aunque la respuesta sea 401 (la revocación se
 * confirma antes de lanzar), deja una sola línea de aviso por familia y esa línea no lleva ni el refresh ni su hash.
 */
@SuppressWarnings("unchecked")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class ReusoDeRefreshE2ETest {
    static final String AVISO = "Refresh reutilizado fuera de la gracia: familia revocada";

    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private ResponseEntity<Map> registrar() {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}", json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r;
    }

    private ResponseEntity<Map> refrescar(String refresh) {
        return http.postForEntity("/v1/auth/refresh", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(refresh), json()), Map.class);
    }

    private String refreshDe(ResponseEntity<Map> r) { return (String) ((Map<?, ?>) r.getBody().get("datos")).get("refresh"); }

    private void assertSesionInvalida(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).containsEntry("codigo", "SESION_INVALIDA");
    }

    private int abiertas() { return jdbc.queryForObject("SELECT count(*) FROM sesion WHERE NOT revocada", Integer.class); }

    private long avisos(CapturedOutput salida) { return salida.getAll().lines().filter(l -> l.contains(AVISO)).count(); }

    /** Hace pasar la gracia de la sesión ya rotada que presenta {@code refresh}. */
    private void vencerLaGracia(String refresh) {
        jdbc.update("UPDATE sesion SET rotada_en = now() - interval '31 seconds' WHERE refresh_hash = ?", TokenOpaco.hash(refresh));
    }

    /** El caso del issue: A→B, el atacante rota B→C, la víctima presenta B pasada la gracia. C muere con B aunque la respuesta sea un 401. */
    @Test void elReusoFueraDeLaGraciaRevocaLaFamiliaYAvisaUnaSolaVezSinElToken(CapturedOutput salida) {
        ResponseEntity<Map> registro = registrar();
        String a = refreshDe(registro);
        String b = refreshDe(refrescar(a));
        String c = refreshDe(refrescar(b));   // el atacante
        vencerLaGracia(b);
        assertThat(abiertas()).as("solo C sigue abierta").isEqualTo(1);

        assertSesionInvalida(refrescar(b));

        assertThat(abiertas()).as("la revocación se confirmó pese al 401").isZero();
        assertSesionInvalida(refrescar(c));
        assertSesionInvalida(refrescar(a));
        assertSesionInvalida(refrescar(b));   // segundo reuso: ya no hay rotación que delatar
        assertThat(avisos(salida)).as("un aviso por familia").isEqualTo(1);
        UUID usuario = jdbc.queryForObject("SELECT id FROM usuario", UUID.class);
        UUID familia = jdbc.queryForObject("SELECT familia_id FROM sesion WHERE refresh_hash = ?", UUID.class, TokenOpaco.hash(b));
        assertThat(salida.getAll()).contains("usuario=" + usuario, "familia=" + familia);
        for (String secreto : new String[]{a, b, c, TokenOpaco.hash(a), TokenOpaco.hash(b), TokenOpaco.hash(c)})
            assertThat(salida.getAll()).as("el log no lleva el refresh ni su hash").doesNotContain(secreto);
    }

    @Test void despuesDeCerrarSesionPresentarElRefreshViejoNoAvisa(CapturedOutput salida) {
        ResponseEntity<Map> registro = registrar();
        String a = refreshDe(registro);
        String b = refreshDe(refrescar(a));
        HttpHeaders conSesion = json();
        conSesion.setBearerAuth((String) ((Map<?, ?>) registro.getBody().get("datos")).get("access"));
        assertThat(http.postForEntity("/v1/auth/logout", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(b), conSesion), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertSesionInvalida(refrescar(a));

        assertThat(avisos(salida)).as("la familia ya estaba cerrada por el logout").isZero();
    }

    @Test void dentroDeLaGraciaNoEsUnReuso(CapturedOutput salida) {
        String a = refreshDe(registrar());
        String b = refreshDe(refrescar(a));

        assertThat(refrescar(a).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(avisos(salida)).isZero();
        assertThat(refrescar(b).getStatusCode()).as("la rama de la primera sigue viva").isEqualTo(HttpStatus.OK);
    }

    @Test void unRefreshVencidoSoloSeRechaza(CapturedOutput salida) {
        String a = refreshDe(registrar());
        String b = refreshDe(refrescar(a));
        vencerLaGracia(a);
        jdbc.update("UPDATE sesion SET expira_en = now() - interval '1 day' WHERE refresh_hash = ?", TokenOpaco.hash(a));

        assertSesionInvalida(refrescar(a));

        assertThat(avisos(salida)).isZero();
        assertThat(refrescar(b).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
