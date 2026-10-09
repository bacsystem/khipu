package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S6 de extremo a extremo: con varias instancias del portal, dos peticiones del mismo navegador pueden refrescar con el mismo refresh casi a la
 * vez. Ninguna de las dos debe cerrar la sesión del usuario; cerrar sesión sí la cierra del todo.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class RefreshConcurrenteE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private String registrar() {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}", json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) ((Map<?, ?>) r.getBody().get("datos")).get("refresh");
    }

    private ResponseEntity<Map> refrescar(String refresh) {
        return http.postForEntity("/v1/auth/refresh", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(refresh), json()), Map.class);
    }

    private String refreshDe(ResponseEntity<Map> r) { return (String) ((Map<?, ?>) r.getBody().get("datos")).get("refresh"); }

    @Test void dosRefreshSimultaneosConElMismoTokenSalenBienYCadaUnoQuedaConSesion() throws Exception {
        String refresh = registrar();
        CyclicBarrier largada = new CyclicBarrier(2);
        List<CompletableFuture<ResponseEntity<Map>>> carreras = List.of(1, 2).stream().map(i -> CompletableFuture.supplyAsync(() -> {
            try { largada.await(); } catch (Exception e) { throw new IllegalStateException(e); }
            return refrescar(refresh);
        })).toList();

        List<ResponseEntity<Map>> respuestas = carreras.stream().map(CompletableFuture::join).toList();

        assertThat(respuestas).extracting(ResponseEntity::getStatusCode).containsOnly(HttpStatus.OK);
        for (ResponseEntity<Map> r : respuestas) assertThat(refrescar(refreshDe(r)).getStatusCode()).as("cada instancia sigue con sesión").isEqualTo(HttpStatus.OK);
    }

    @Test void elMismoRefreshUnPocoDespuesTodaviaSirve() {
        String refresh = registrar();
        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void despuesDeCerrarSesionElRefreshAnteriorYaNoSirve() {
        String refresh = registrar();
        ResponseEntity<Map> renovada = refrescar(refresh);
        String nuevo = refreshDe(renovada);
        HttpHeaders conSesion = json();
        conSesion.setBearerAuth((String) ((Map<?, ?>) renovada.getBody().get("datos")).get("access"));

        assertThat(http.postForEntity("/v1/auth/logout", new HttpEntity<>("{\"refresh\":\"%s\"}".formatted(nuevo), conSesion), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refrescar(nuevo).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void pasadaLaGraciaElRefreshRotadoYaNoSirve() {
        String refresh = registrar();
        refrescar(refresh);
        jdbc.update("UPDATE sesion SET rotada_en = now() - interval '31 seconds' WHERE rotada_en IS NOT NULL");

        assertThat(refrescar(refresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
