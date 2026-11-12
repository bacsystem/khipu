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
import pe.factura.application.port.in.PurgarTokensDeAccesoUseCase;
import pe.factura.application.service.TokenOpaco;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #634 de extremo a extremo: borrar los enlaces vencidos no cambia ninguna respuesta de la API ({@code 422 TOKEN_INVALIDO} igual con la fila presente y con
 * la fila borrada), un enlace vigente no se toca aunque esté usado, y las dos purgas reciben la retención de {@code app.acceso-retencion-dias} (7 días).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class LimpiezaDeTokensDeAccesoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PurgarTokensDeAccesoUseCase purga;

    UUID usuario;

    @BeforeEach void usuario() {
        jdbc.update("TRUNCATE token_recuperacion, token_verificacion, sesion, usuario, cuenta CASCADE");
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}", json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@negocio.pe'", UUID.class);
        jdbc.update("TRUNCATE token_verificacion, token_recuperacion");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    /** Crea un token que expira en {@code horas} horas (negativas: ya venció) y devuelve el valor en claro que llegaría por correo. */
    private String token(String tabla, int horas, boolean usado) {
        String claro = TokenOpaco.generar();
        jdbc.update("INSERT INTO " + tabla + " (token_hash, usuario_id, expira_en, usado) VALUES (?, ?, now() + make_interval(hours => ?), ?)",
                TokenOpaco.hash(claro), usuario, horas, usado);
        return claro;
    }

    private boolean existe(String tabla, String claro) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE token_hash = ?", Integer.class, TokenOpaco.hash(claro)) == 1;
    }

    private ResponseEntity<Map> restablecer(String token) {
        return http.postForEntity("/v1/auth/restablecer", new HttpEntity<>("{\"token\":\"%s\",\"password\":\"OtraClave123\"}".formatted(token), json()), Map.class);
    }

    private ResponseEntity<Map> verificar(String token) {
        return http.postForEntity("/v1/auth/verificar", new HttpEntity<>("{\"token\":\"%s\"}".formatted(token), json()), Map.class);
    }

    /** Devuelve el mensaje, para poder comprobar que la fila borrada no cambia ni el texto: si cambiara, quien consulta distinguiría un enlace purgado de uno vencido. */
    private String assertTokenInvalido(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody()).containsEntry("codigo", "TOKEN_INVALIDO");
        assertThat(r.getBody().get("mensaje")).isInstanceOf(String.class);
        return (String) r.getBody().get("mensaje");
    }

    private static final int HORAS_DE_UN_DIA = 24;

    @Test void unaRecuperacionVencidaDaLaMismaRespuestaAntesYDespuesDeBorrarla() {
        String vencida = token("token_recuperacion", -8 * HORAS_DE_UN_DIA, false);

        String antes = assertTokenInvalido(restablecer(vencida));
        assertThat(purga.purgar()).isEqualTo(1);
        assertThat(existe("token_recuperacion", vencida)).isFalse();
        assertThat(assertTokenInvalido(restablecer(vencida))).as("mismo texto con la fila borrada").isEqualTo(antes);
    }

    @Test void unaVerificacionVencidaDaLaMismaRespuestaAntesYDespuesDeBorrarla() {
        String vencida = token("token_verificacion", -8 * HORAS_DE_UN_DIA, true);

        String antes = assertTokenInvalido(verificar(vencida));
        assertThat(purga.purgar()).isEqualTo(1);
        assertThat(existe("token_verificacion", vencida)).isFalse();
        assertThat(assertTokenInvalido(verificar(vencida))).as("mismo texto con la fila borrada").isEqualTo(antes);
    }

    @Test void respetaLaRetencionDeSieteDiasEnLasDosTablas() {
        String recuperacionReciente = token("token_recuperacion", -3 * HORAS_DE_UN_DIA, false);
        String verificacionReciente = token("token_verificacion", -3 * HORAS_DE_UN_DIA, false);
        String recuperacionVieja = token("token_recuperacion", -8 * HORAS_DE_UN_DIA, false);
        String verificacionVieja = token("token_verificacion", -8 * HORAS_DE_UN_DIA, false);

        assertThat(purga.purgar()).isEqualTo(2);

        assertThat(existe("token_recuperacion", recuperacionReciente)).as("vencida pero dentro de los 7 días").isTrue();
        assertThat(existe("token_verificacion", verificacionReciente)).isTrue();
        assertThat(existe("token_recuperacion", recuperacionVieja)).isFalse();
        assertThat(existe("token_verificacion", verificacionVieja)).isFalse();
    }

    /** El «ya se usó» solo sale con un enlace vigente: la purga no puede llevárselo, o la respuesta pasaría a ser la de un enlace inexistente. */
    @Test void unEnlaceVigenteYUsadoNoSeBorraYSigueDandoSuRespuesta() {
        String verificacionUsada = token("token_verificacion", 10, true);
        String recuperacionUsada = token("token_recuperacion", 1, true);

        assertThat(purga.purgar()).isZero();

        assertThat(existe("token_verificacion", verificacionUsada)).isTrue();
        assertThat(existe("token_recuperacion", recuperacionUsada)).isTrue();
        assertTokenInvalido(verificar(verificacionUsada));
        assertTokenInvalido(restablecer(recuperacionUsada));
    }

    @Test void unEnlaceVigenteSinUsarSigueFuncionandoDespuesDeLaLimpieza() {
        String vigente = token("token_recuperacion", 1, false);
        token("token_recuperacion", -9 * HORAS_DE_UN_DIA, false);

        assertThat(purga.purgar()).isEqualTo(1);

        assertThat(restablecer(vigente).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
