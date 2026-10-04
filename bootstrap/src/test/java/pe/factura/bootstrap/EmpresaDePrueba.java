package pe.factura.bootstrap;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Una empresa lista para emitir por HTTP: alta con la clave de plataforma, credenciales SOL, serie F001 y certificado de prueba. */
final class EmpresaDePrueba {
    private EmpresaDePrueba() {}

    /** Devuelve la API key de la empresa. */
    static String provisionar(TestRestTemplate http) throws Exception {
        HttpHeaders admin = new HttpHeaders(); admin.set("X-Platform-Key", "plataforma-test"); admin.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> creado = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", admin), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = (String) ((Map<?, ?>) creado.getBody().get("datos")).get("api_key");

        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/v1/empresa/credenciales-sol", HttpMethod.PUT, new HttpEntity<>("{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        byte[] p12 = EmpresaDePrueba.class.getResourceAsStream("/test-cert.p12").readAllBytes();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(p12) { @Override public String getFilename() { return "cert.p12"; } });
        form.add("clave", "test1234");
        HttpHeaders mh = new HttpHeaders(); mh.set("X-Api-Key", apiKey); mh.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(http.postForEntity("/v1/empresa/certificado", new HttpEntity<>(form, mh), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return apiKey;
    }
}
