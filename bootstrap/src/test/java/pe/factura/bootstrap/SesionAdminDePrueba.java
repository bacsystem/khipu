package pe.factura.bootstrap;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import pe.factura.adapters.crypto.TotpRfc6238;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login completo de un administrador por HTTP (#177): contraseña, configuración del segundo factor y primer código, generado como lo
 * haría la app del teléfono. Para los e2e que necesitan una sesión de administrador y no prueban el login en sí.
 */
@SuppressWarnings("unchecked")
final class SesionAdminDePrueba {
    static final TotpRfc6238 TOTP = new TotpRfc6238();

    private SesionAdminDePrueba() {}

    /** Los {@code datos} de la respuesta de la sesión: {@code access_token}, {@code expira_en}, {@code administrador}, {@code codigos_recuperacion}. */
    static Map<String, Object> entrar(TestRestTemplate http, String email, String password) {
        String desafio = (String) datos(post(http, "/v1/admin/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).get("desafio");
        String secreto = (String) datos(post(http, "/v1/admin/auth/segundo-factor/configurar", "{\"desafio\":\"%s\"}".formatted(desafio))).get("secreto");
        return datos(post(http, "/v1/admin/auth/segundo-factor/confirmar",
                "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigoActual(secreto))));
    }

    static String codigoActual(String secreto) { return TOTP.codigo(secreto, Instant.now().getEpochSecond() / 30); }

    static ResponseEntity<Map> post(TestRestTemplate http, String ruta, String json) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(ruta, new HttpEntity<>(json, h), Map.class);
    }

    private static Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }
}
