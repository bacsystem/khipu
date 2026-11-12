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
 * #636 de extremo a extremo, con el valor por defecto (30 min de inactividad + 15 del access = corte a los 45): una sesión sin refrescar más allá del corte
 * se revoca por completo aunque la respuesta sea un 401, deja una sola línea de aviso con ids y no se confunde con un reuso (#635).
 */
@SuppressWarnings("unchecked")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class InactividadDeSesionE2ETest {
    static final String AVISO = "Sesión cerrada por inactividad";

    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

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

    private void assertSesionInvalida(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).containsEntry("codigo", "SESION_INVALIDA");
    }

    private int abiertas() { return jdbc.queryForObject("SELECT count(*) FROM sesion WHERE NOT revocada", Integer.class); }

    private long avisos(CapturedOutput salida, String texto) { return salida.getAll().lines().filter(l -> l.contains(texto)).count(); }

    /** Como si la sesión llevara {@code minutos} sin refrescarse: se adelanta el nacimiento de sus filas. */
    private void envejecer(int minutos) {
        jdbc.update("UPDATE sesion SET created_at = created_at - make_interval(mins => ?)", minutos);
    }

    @Test void pasadoElCorteElRefreshCierraLaFamiliaYAvisaUnaSolaVezSinElToken(CapturedOutput salida) {
        String a = registrar();
        String b = refreshDe(refrescar(a));
        String c = refreshDe(refrescar(b));
        envejecer(46);   // 30 min de inactividad + 15 de vida del access: a los 45 corta

        assertSesionInvalida(refrescar(c));

        assertThat(abiertas()).as("la revocación se confirmó pese al 401").isZero();
        assertSesionInvalida(refrescar(c));   // la misma fila, ya revocada: sin nuevo aviso
        assertSesionInvalida(refrescar(b));
        assertThat(avisos(salida, AVISO)).as("un aviso por familia").isEqualTo(1);
        assertThat(avisos(salida, "Refresh reutilizado")).as("no es un reuso").isZero();
        UUID usuario = jdbc.queryForObject("SELECT id FROM usuario", UUID.class);
        UUID familia = jdbc.queryForObject("SELECT familia_id FROM sesion WHERE refresh_hash = ?", UUID.class, TokenOpaco.hash(c));
        assertThat(salida.getAll()).contains("usuario=" + usuario, "familia=" + familia);
        for (String secreto : new String[]{a, b, c, TokenOpaco.hash(a), TokenOpaco.hash(b), TokenOpaco.hash(c)})
            assertThat(salida.getAll()).as("el log no lleva el refresh ni su hash").doesNotContain(secreto);

        // Lo que ve el usuario: el corte no lo deja afuera. Vuelve a entrar con su contraseña y obtiene una familia nueva.
        jdbc.update("UPDATE usuario SET correo_verificado_at = now()");
        ResponseEntity<Map> login = http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}", json()), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String nuevo = refreshDe(login);
        assertThat(refrescar(nuevo).getStatusCode()).as("el refresh del login nuevo sirve").isEqualTo(HttpStatus.OK);
        UUID familiaNueva = jdbc.queryForObject("SELECT familia_id FROM sesion WHERE refresh_hash = ?", UUID.class, TokenOpaco.hash(nuevo));
        assertThat(familiaNueva).as("una familia distinta de la que cortó el servidor").isNotNull().isNotEqualTo(familia);
    }

    @Test void antesDelCorteElRefreshRota(CapturedOutput salida) {
        String a = registrar();
        envejecer(44);

        ResponseEntity<Map> r = refrescar(a);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(avisos(salida, AVISO)).isZero();
        assertThat(refrescar(refreshDe(r)).getStatusCode()).as("el nuevo refresh nace ahora y sirve").isEqualTo(HttpStatus.OK);
    }

    /** La fila creada con el reloj de la app es la que se mide: la que acaba de rotar (A, en gracia) no corta aunque su sesión sea vieja. */
    @Test void unaFilaEnGraciaNoCortaAunqueSeaVieja() {
        String a = registrar();
        String b = refreshDe(refrescar(a));
        envejecer(46);
        // B quedó vieja y A está en gracia (se rotó hace segundos de verdad): A rota, B no se revoca.
        assertThat(refrescar(a).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(abiertas()).as("B y la nueva rama siguen abiertas").isEqualTo(2);
        assertThat(b).isNotBlank();
    }
}
