package pe.factura.bootstrap.homologacion;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.factura.adapters.sunat.ZipUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suite de homologación (#32): emite cada escenario de {@link EscenariosFactura} contra e-beta con el flujo completo
 * (API → firma → XSD → SOAP) y exige CDR {@code 0} sin observaciones. Guarda XML y CDR de cada caso en
 * {@code build/homologacion/<escenario>/} y un {@code RESUMEN.md} con el resultado por escenario (evidencia para SUNAT).
 * <p>
 * Corre con {@code ./gradlew :bootstrap:homologacion} (Docker + salida a Internet); {@code test} la excluye por la etiqueta.
 * Variables opcionales: {@code HOMOLOGACION_RUC} (emisor, por defecto el del certificado de prueba), {@code HOMOLOGACION_CERT}
 * y {@code HOMOLOGACION_CERT_CLAVE} (PKCS#12 con OU = RUC; por defecto {@code test-cert.p12}), {@code HOMOLOGACION_SERIE}
 * (por defecto una serie única por minuto para que e-beta no responda 1033 al reenviar el mismo número con otros datos).
 */
@Tag("homologacion")
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class HomologacionBetaTest {
    // Con PER_CLASS la instancia (y el contexto de Spring) se crea antes de los callbacks de @Testcontainers, así que el
    // contenedor se arranca en el inicializador estático; Ryuk lo elimina al terminar la JVM.
    @ServiceConnection static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
    static { pg.start(); }

    static final String RUC = env("HOMOLOGACION_RUC", "20100066603");
    static final String SERIE = env("HOMOLOGACION_SERIE", serieUnica());
    /** Serie del anexo 0002 (escenario 24): misma base que SERIE con la segunda letra cambiada, única por ejecución. */
    static final String SERIE_ANEXO = "FA" + SERIE.substring(2);
    static final Path SALIDA = Path.of(System.getProperty("homologacion.salida", "build/homologacion"));

    @Autowired TestRestTemplate http;
    HttpHeaders api;
    final List<String[]> resumen = new ArrayList<>();
    /** Números aceptados por SUNAT que reutilizan escenarios posteriores (anticipo → final, facturas → notas). */
    final Map<String, Long> numeros = new HashMap<>();
    /** Ids de comprobantes de la corrida que usan pasos posteriores (baja). */
    final Map<String, String> ids = new HashMap<>();

    /** Variable de entorno o valor por defecto; GitHub Actions pasa los inputs vacíos como "" y no como ausentes. */
    static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v == null || v.isBlank() ? porDefecto : v;
    }

    /** F + 3 caracteres base 36 del minuto actual (ciclo de ~32 días): distinta en cada ejecución nocturna. */
    static String serieUnica() {
        long minutos = System.currentTimeMillis() / 60_000 % (36L * 36 * 36);
        return "F" + String.format("%3s", Long.toString(minutos, 36).toUpperCase()).replace(' ', '0');
    }

    @BeforeAll void provisionar() throws IOException {
        Files.createDirectories(SALIDA);
        HttpHeaders admin = new HttpHeaders(); admin.set("X-Platform-Key", "plataforma-test"); admin.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> creado = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"" + RUC + "\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", admin), Map.class);
        assertThat(creado.getStatusCode()).as("alta del tenant").isEqualTo(HttpStatus.CREATED);
        String apiKey = (String) ((Map<?, ?>) creado.getBody().get("datos")).get("api_key");
        api = new HttpHeaders(); api.set("X-Api-Key", apiKey); api.setContentType(MediaType.APPLICATION_JSON);

        assertThat(http.exchange("/v1/empresa/credenciales-sol", HttpMethod.PUT, new HttpEntity<>("{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}", api), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/v1/empresa/datos-fiscales", HttpMethod.PUT, new HttpEntity<>(
                "{\"domicilio\":{\"ubigeo\":\"150101\",\"direccion\":\"AV. LIMA 123\"},\"cuenta_detracciones\":\"00-000-123456\",\"nombre_comercial\":\"KHIPU PRUEBAS\"}", api), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String tipo : List.of("01", "07", "08"))   // la misma serie F### vale para factura, NC y ND (regla 1001); cada tipo numera aparte
            assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"" + tipo + "\",\"serie\":\"" + SERIE + "\",\"correlativo_inicial\":0}", api), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Establecimiento anexo 0002 con su propia serie de factura (#80).
        assertThat(http.postForEntity("/v1/empresa/establecimientos", new HttpEntity<>(
                "{\"codigo\":\"0002\",\"nombre\":\"Tienda Miraflores\",\"domicilio\":{\"ubigeo\":\"150122\",\"direccion\":\"AV. LARCO 345\"}}", api), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"01\",\"serie\":\"" + SERIE_ANEXO + "\",\"correlativo_inicial\":0,\"establecimiento\":\"0002\"}", api), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String certPath = env("HOMOLOGACION_CERT", null);
        byte[] p12 = certPath == null ? getClass().getResourceAsStream("/test-cert.p12").readAllBytes() : Files.readAllBytes(Path.of(certPath));
        String clave = env("HOMOLOGACION_CERT_CLAVE", "test1234");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(p12) { @Override public String getFilename() { return "cert.p12"; } });
        form.add("clave", clave);
        HttpHeaders mh = new HttpHeaders(); mh.set("X-Api-Key", apiKey); mh.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(http.postForEntity("/v1/empresa/certificado", new HttpEntity<>(form, mh), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    Stream<EscenariosFactura.Escenario> escenarios() {
        return EscenariosFactura.todos(SERIE, SERIE_ANEXO, LocalDate.now(ZoneId.of("America/Lima"))).stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("escenarios")
    void escenario(EscenariosFactura.Escenario e) throws IOException {
        // Los datos fiscales se reescriben por escenario para activar/desactivar la tasa especial del IGV (#84).
        assertThat(http.exchange("/v1/empresa/datos-fiscales", HttpMethod.PUT, new HttpEntity<>(
                "{\"domicilio\":{\"ubigeo\":\"150101\",\"direccion\":\"AV. LIMA 123\"},\"cuenta_detracciones\":\"00-000-123456\",\"nombre_comercial\":\"KHIPU PRUEBAS\",\"padron_tasa_especial_igv\":"
                + e.tasaEspecial() + "}", api), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        String cuerpo = e.cuerpo();
        for (Map.Entry<String, Long> n : numeros.entrySet()) cuerpo = cuerpo.replace("${" + n.getKey() + "}", Long.toString(n.getValue()));
        ResponseEntity<Map> r = http.postForEntity(e.endpoint(), new HttpEntity<>(cuerpo, api), Map.class);
        Map<?, ?> datos = r.getBody() == null ? Map.of() : (Map<?, ?>) r.getBody().getOrDefault("datos", Map.of());
        String estado = String.valueOf(datos.get("estado_documento"));
        Map<?, ?> cdr = datos.get("cdr") instanceof Map<?, ?> m ? m : Map.of();
        List<String> observaciones = cdr.get("observaciones") instanceof List<?> l ? (List<String>) l : List.of();
        String codigo = String.valueOf(cdr.get("codigo"));
        String detalle = !r.getStatusCode().is2xxSuccessful() ? "HTTP " + r.getStatusCode().value() + " " + r.getBody()
                : cdr.isEmpty() ? "sin CDR: " + datos.get("ultimo_error")
                : codigo + " - " + cdr.get("descripcion") + (observaciones.isEmpty() ? "" : " | obs: " + observaciones);
        resumen.add(new String[]{e.id(), e.descripcion(), estado, detalle});

        if (datos.get("id") != null) guardarEvidencia(e.id(), (String) datos.get("id"), (String) datos.get("nombre_archivo"), estado, detalle);
        if (datos.get("numero") instanceof Number num) {
            if (e.id().startsWith("01-")) numeros.put("GRAVADA", num.longValue());
            if (e.id().startsWith("02-")) ids.put("EXONERADA", String.valueOf(datos.get("id")));
            if (e.id().startsWith("04-")) numeros.put("MIXTA", num.longValue());
            if (e.id().startsWith("09-")) numeros.put("CREDITO", num.longValue());
            if (e.id().startsWith("16-")) numeros.put("ANTICIPO", num.longValue());
        }

        assertThat(r.getStatusCode()).as(e.id() + ": " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(codigo).as(e.id() + ": código CDR").isEqualTo("0");
        if (e.observacionEsperada() == null) {
            assertThat(estado).as(e.id() + ": " + detalle).isEqualTo("ACEPTADO");
            assertThat(observaciones).as(e.id() + ": observaciones SUNAT").isEmpty();
        } else {
            // e-beta no cruza el padrón (2026-09-19: aceptó al 10.5 % sin 4439); producción sí lo haría. Ambos resultados valen:
            // lo que se comprueba es que el XML con la tasa reducida pasa las reglas de cálculo (3279, 3291, 3462).
            assertThat(estado).as(e.id() + ": " + detalle).isIn("ACEPTADO", "ACEPTADO_CON_OBS");
            assertThat(observaciones).as(e.id() + ": solo la observación esperada " + e.observacionEsperada())
                    .allSatisfy(o -> assertThat(o).startsWith(e.observacionEsperada()));
        }
    }

    private void guardarEvidencia(String escenario, String id, String nombreArchivo, String estado, String detalle) throws IOException {
        Path dir = SALIDA.resolve(escenario);
        Files.createDirectories(dir);
        ResponseEntity<byte[]> xml = http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(api), byte[].class);
        if (xml.getStatusCode().is2xxSuccessful()) Files.write(dir.resolve(nombreArchivo + ".xml"), xml.getBody());
        ResponseEntity<byte[]> cdr = http.exchange("/v1/facturas/" + id + "/cdr", HttpMethod.GET, new HttpEntity<>(api), byte[].class);
        if (cdr.getStatusCode().is2xxSuccessful()) Files.write(dir.resolve("R-" + nombreArchivo + ".xml"), ZipUtil.extraerPrimero(cdr.getBody(), ".xml"));
        Files.writeString(dir.resolve("resultado.txt"), estado + "\n" + detalle + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Comunicación de baja del escenario 02 (exonerada): sendSummary + getStatus contra e-beta. Va al final (orden por nombre)
     * porque necesita la factura ya aceptada; si SUNAT sigue procesando (98), se reconsulta unas veces antes de dar por fallido.
     */
    @org.junit.jupiter.api.Test void zzComunicacionDeBaja() throws Exception {
        String id = ids.get("EXONERADA");
        assertThat(id).as("la factura exonerada (02) debe haberse emitido").isNotNull();
        ResponseEntity<Map> r = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Homologación: baja de prueba\"}", api), Map.class);
        Map<?, ?> datos = r.getBody() == null ? Map.of() : (Map<?, ?>) r.getBody().getOrDefault("datos", Map.of());
        String bajaId = String.valueOf(datos.get("id"));
        for (int i = 0; i < 6 && "ENVIADA".equals(datos.get("estado")); i++) {
            Thread.sleep(5_000);
            ResponseEntity<Map> again = http.exchange("/v1/bajas/" + bajaId, HttpMethod.GET, new HttpEntity<>(api), Map.class);
            datos = again.getBody() == null ? Map.of() : (Map<?, ?>) again.getBody().getOrDefault("datos", Map.of());
        }
        Map<?, ?> cdr = datos.get("cdr") instanceof Map<?, ?> m ? m : Map.of();
        String detalle = r.getStatusCode().is2xxSuccessful() ? cdr.get("codigo") + " - " + cdr.get("descripcion") + (datos.get("ultimo_error") == null ? "" : " | " + datos.get("ultimo_error"))
                : "HTTP " + r.getStatusCode().value() + " " + r.getBody();
        resumen.add(new String[]{"22-baja", "Comunicación de baja (RA) de la factura exonerada", String.valueOf(datos.get("estado")), detalle});
        Path dir = SALIDA.resolve("22-baja");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("resultado.txt"), datos.get("estado") + "\n" + detalle + "\n", StandardCharsets.UTF_8);

        assertThat(r.getStatusCode()).as("22-baja: " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(datos.get("estado")).as("22-baja: " + detalle).isEqualTo("ACEPTADA");
        assertThat(String.valueOf(cdr.get("codigo"))).isEqualTo("0");
        ResponseEntity<Map> factura = http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(api), Map.class);
        assertThat(((Map<?, ?>) factura.getBody().get("datos")).get("estado_documento")).as("la factura queda ANULADA").isEqualTo("ANULADO");
    }

    @AfterAll void escribirResumen() throws IOException {
        StringBuilder md = new StringBuilder("# Homologación e-beta — factura y notas\n\n")
                .append("RUC emisor `").append(RUC).append("`, serie `").append(SERIE).append("`, ").append(LocalDate.now(ZoneId.of("America/Lima"))).append("\n\n")
                .append("| Escenario | Caso | Estado | CDR |\n|---|---|---|---|\n");
        for (String[] f : resumen) md.append("| ").append(f[0]).append(" | ").append(f[1]).append(" | ").append(f[2]).append(" | ").append(f[3].replace("|", "\\|")).append(" |\n");
        long ok = resumen.stream().filter(f -> ("ACEPTADO".equals(f[2]) || "ACEPTADA".equals(f[2])) && f[3].startsWith("0 -") && !f[3].contains("obs:")).count();
        long conObsEsperada = resumen.stream().filter(f -> "ACEPTADO_CON_OBS".equals(f[2]) && f[3].contains("obs: [4439")).count();
        md.append("\n**").append(ok).append("/").append(resumen.size()).append(" escenarios con CDR 0 sin observaciones");
        if (conObsEsperada > 0) md.append("; ").append(conObsEsperada).append(" con la observación 4439 esperada (tasa reducida sin padrón)");
        md.append(".**\n");
        Files.writeString(SALIDA.resolve("RESUMEN.md"), md.toString(), StandardCharsets.UTF_8);
    }
}
