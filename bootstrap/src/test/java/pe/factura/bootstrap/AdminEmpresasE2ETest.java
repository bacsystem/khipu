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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listado de empresas del backoffice (#185) de extremo a extremo: HTTP real, filtros reales, el reloj real de la aplicación (Lima) y Postgres
 * real. Trae el RUC y la situación de TODAS las empresas, así que solo lo leen la clave de plataforma y un administrador.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AdminEmpresasE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** «Hoy» como lo ve la aplicación (zona de Lima), no la de la máquina que corre el test. */
    static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private ResponseEntity<Map> listar(HttpHeaders h, String consulta) {
        return http.exchange("/v1/admin/empresas" + consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private List<Map<String, Object>> datos(ResponseEntity<Map> r) { return (List<Map<String, Object>>) r.getBody().get("datos"); }

    private List<String> rucs(ResponseEntity<Map> r) { return datos(r).stream().map(e -> (String) e.get("ruc")).toList(); }

    /** Una cuenta de cliente real, registrada por el portal, con una empresa dada de alta con su JWT. Devuelve su JWT. */
    private String clienteConEmpresa(String email, String ruc, String razonSocial) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        ResponseEntity<Map> empresa = http.postForEntity("/v1/empresas", new HttpEntity<>(
                "{\"ruc\":\"%s\",\"razon_social\":\"%s\",\"entorno\":\"BETA\"}".formatted(ruc, razonSocial), conBearer(access)), Map.class);
        assertThat(empresa.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return access;
    }

    private void certificado(String ruc, LocalDate hasta) {
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = ? WHERE ruc = ?", new byte[]{1}, new byte[]{2}, java.sql.Date.valueOf(hasta), ruc);
    }

    private void documento(String ruc, int numero, LocalDate fecha) {
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                SELECT ?, id, '01', 'F001', ?, ?, 'ACEPTADO', ? FROM tenant WHERE ruc = ?""", UUID.randomUUID(), numero, java.sql.Date.valueOf(fecha), "doc-" + numero, ruc);
    }

    @Test void laClaveDePlataformaVeLasEmpresasConSuSituacion() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION', sol_usuario_enc = ?, sol_clave_enc = ? WHERE ruc = '20100066603'", new byte[]{3}, new byte[]{4});
        certificado("20100066603", HOY.plusDays(10));
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo) SELECT id, '01', 'F001' FROM tenant WHERE ruc = '20100066603'");
        documento("20100066603", 1, HOY);

        ResponseEntity<Map> r = listar(conClaveDePlataforma(), "");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(datos(r).get(0)).containsEntry("ruc", "20100066603").containsEntry("razon_social", "COMERCIAL ANDINA SAC")
                .containsEntry("cuenta_nombre", "Mi negocio").containsEntry("entorno", "PRODUCCION").containsEntry("certificado", "POR_VENCER")
                .containsEntry("certificado_vigente_hasta", HOY.plusDays(10).toString()).containsEntry("certificado_dias_restantes", 10)
                .containsEntry("tiene_credenciales_sol", true).containsEntry("series", 1).containsEntry("ultima_emision", HOY.toString())
                .containsKeys("id", "cuenta_id", "comprobantes_del_mes");
    }

    /** Una empresa dada de alta por una integración no tiene cuenta: sale igual, sin los campos de la cuenta. */
    @Test void unaEmpresaDeIntegracionSinCuentaTambienSale() {
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        assertThat(tenant.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> r = listar(conClaveDePlataforma(), "");

        assertThat(datos(r)).hasSize(1);
        assertThat(datos(r).get(0)).containsEntry("ruc", "20100066611").containsEntry("certificado", "SIN_CERTIFICADO").doesNotContainKeys("cuenta_id", "cuenta_nombre");
    }

    @Test void filtraPorEntornoYPorEstadoDelCertificadoYElTotalLoRefleja() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        clienteConEmpresa("luis@otro.pe", "20100066620", "FERRETERIA LUNA SAC");
        clienteConEmpresa("eva@tercero.pe", "20100066638", "BODEGA SOL SAC");
        certificado("20100066603", HOY.minusDays(1));
        certificado("20100066620", HOY.plusDays(29));
        certificado("20100066638", HOY.plusDays(30));
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION' WHERE ruc IN ('20100066603', '20100066620')");

        assertThat(rucs(listar(conClaveDePlataforma(), "?certificado=VENCIDO"))).containsExactly("20100066603");
        assertThat(rucs(listar(conClaveDePlataforma(), "?certificado=POR_VENCER"))).containsExactly("20100066620");
        assertThat(rucs(listar(conClaveDePlataforma(), "?certificado=VIGENTE"))).containsExactly("20100066638");
        ResponseEntity<Map> enProduccion = listar(conClaveDePlataforma(), "?entorno=PRODUCCION");
        assertThat(rucs(enProduccion)).containsExactlyInAnyOrder("20100066603", "20100066620");
        assertThat(enProduccion.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        ResponseEntity<Map> ambos = listar(conClaveDePlataforma(), "?entorno=PRODUCCION&certificado=POR_VENCER");
        assertThat(rucs(ambos)).containsExactly("20100066620");
        assertThat(ambos.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        ResponseEntity<Map> ninguna = listar(conClaveDePlataforma(), "?entorno=BETA&certificado=VENCIDO");
        assertThat(ninguna.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ninguna.getHeaders().getFirst("X-Total-Count")).isEqualTo("0");
        assertThat(datos(ninguna)).isEmpty();
    }

    @Test void losComprobantesDelMesSonLosDelMesDeHoy() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        documento("20100066603", 1, HOY.withDayOfMonth(1));
        documento("20100066603", 2, HOY);
        documento("20100066603", 3, HOY.withDayOfMonth(1).minusDays(1));
        documento("20100066603", 4, HOY.withDayOfMonth(1).plusMonths(1));

        assertThat(datos(listar(conClaveDePlataforma(), "")).get(0)).containsEntry("comprobantes_del_mes", 2);
    }

    @Test void unValorDeFiltroQueNoExisteEs400() {
        assertThat(listar(conClaveDePlataforma(), "?entorno=STAGING").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(listar(conClaveDePlataforma(), "?certificado=CADUCADO").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void paginaConElTotalIntacto() {
        List<String> rucsValidos = List.of("20100066603", "20100066611", "20100066620");
        for (int i = 0; i < rucsValidos.size(); i++) clienteConEmpresa("c" + i + "@x.pe", rucsValidos.get(i), "EMPRESA " + i);

        ResponseEntity<Map> segunda = listar(conClaveDePlataforma(), "?pagina=2&por_pagina=2");

        assertThat(segunda.getHeaders().getFirst("X-Total-Count")).isEqualTo("3");
        assertThat(datos(segunda)).hasSize(1);
    }

    /** El listado no puede dejar salir lo cifrado ni las claves: del certificado y de las credenciales solo se sabe si están. */
    @Test void laRespuestaNoTrataNingunSecreto() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ?, sol_clave_enc = ? WHERE ruc = '20100066603'", "SECRETO-SOL-USUARIO".getBytes(), "SECRETO-SOL-CLAVE".getBytes());
        certificado("20100066603", HOY.plusDays(100));

        String cuerpo = http.exchange("/v1/admin/empresas", HttpMethod.GET, new HttpEntity<>(conClaveDePlataforma()), String.class).getBody();

        assertThat(cuerpo).doesNotContain("SECRETO", "pkcs12", "password", "hash", "api_key");
    }

    @Test void unAdministradorConSesionTambienPuedeLeerlo() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        assertThat(listar(conBearer(token), "").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void nadieMasLoLee() {
        String jwtDeCliente = clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", apiKey);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        assertThat(listar(json(), "").getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conBearer(jwtDeCliente), "").getStatusCode()).as("JWT de un cliente del portal").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conApiKey, "").getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conClaveErronea, "").getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- #186: detalle ------------------------------------------------------------------------------------------------------------

    private UUID idDe(String ruc) { return jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc); }

    private ResponseEntity<Map> abrir(HttpHeaders h, Object id) {
        return http.exchange("/v1/admin/empresas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private Map<String, Object> datosDe(ResponseEntity<Map> r) { return (Map<String, Object>) r.getBody().get("datos"); }

    @Test void abrirUnaEmpresaMuestraTodoLoQueVeSuDueno() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        clienteConEmpresa("luis@otro.pe", "20100066611", "FERRETERIA LUNA SAC");
        UUID id = idDe("20100066603");
        jdbc.update("""
                UPDATE tenant SET entorno = 'PRODUCCION', nombre_comercial = 'ANDINA', cuenta_detracciones = '00-123-456789',
                       dom_ubigeo = '150122', dom_direccion = 'AV. LARCO 345', dom_distrito = 'MIRAFLORES', dom_provincia = 'LIMA', dom_departamento = 'LIMA',
                       dom_establecimiento = '0000', pdf_plantilla = 'MODERNO', pdf_logo_key = 'logos/guardado-en-secreto.png',
                       sol_usuario_enc = ?, sol_clave_enc = ?, cert_pkcs12_enc = ?, cert_clave_enc = ?, cert_vigencia_hasta = ?
                WHERE id = ?""", new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}, java.sql.Date.valueOf(HOY.plusDays(10)), id);
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero) VALUES (?, '01', 'F001', 12)", id);
        jdbc.update("""
                INSERT INTO establecimiento (tenant_id, codigo, nombre, dom_ubigeo, dom_direccion) VALUES (?, '0002', 'Tienda Surco', '150140', 'AV. CAMINOS 100')""", id);
        UUID documento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, intentos, cdr_codigo, cdr_descripcion, cdr_observaciones)
                VALUES (?, ?, '01', 'F001', 12, ?, 'ACEPTADO_CON_OBS', 'doc-12', 2, '0', 'La Factura ha sido aceptada', '["4287 - El dato ingresado no cumple"]'::jsonb)""",
                documento, id, java.sql.Date.valueOf(HOY));
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total)
                VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE SAC', 0, 0, 0, 0, 118.00)""", documento);
        jdbc.update("INSERT INTO evento_documento (documento_id, estado_anterior, estado_nuevo, detalle) VALUES (?, 'FIRMADO', 'ACEPTADO_CON_OBS', 'CDR recibido')", documento);
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, intentos, siguiente_intento, ultimo_error) VALUES (?, ?, 'ENVIAR', 3, now(), 'SUNAT no responde')", id, documento);

        ResponseEntity<Map> r = abrir(conClaveDePlataforma(), id);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = datosDe(r);
        assertThat(d).containsEntry("id", id.toString()).containsEntry("ruc", "20100066603").containsEntry("razon_social", "COMERCIAL ANDINA SAC")
                .containsEntry("nombre_comercial", "ANDINA").containsEntry("entorno", "PRODUCCION").containsEntry("cuenta_nombre", "Mi negocio")
                .containsEntry("certificado", "POR_VENCER").containsEntry("certificado_dias_restantes", 10).containsEntry("tiene_credenciales_sol", true)
                .containsEntry("cuenta_detracciones", "00-123-456789");
        assertThat((Map<String, Object>) d.get("domicilio")).containsEntry("ubigeo", "150122").containsEntry("direccion", "AV. LARCO 345").containsEntry("codigo_establecimiento", "0000");
        assertThat((Map<String, Object>) d.get("pdf")).containsEntry("plantilla", "MODERNO").containsEntry("tiene_logo", true);
        assertThat((List<Map<String, Object>>) d.get("series")).hasSize(1);
        assertThat(((List<Map<String, Object>>) d.get("series")).get(0)).containsEntry("codigo", "F001").containsEntry("ultimo_numero", 12);
        assertThat(((List<Map<String, Object>>) d.get("establecimientos")).get(0)).containsEntry("nombre", "Tienda Surco").containsEntry("codigo", "0002");
        List<Map<String, Object>> comprobantes = (List<Map<String, Object>>) d.get("comprobantes");
        assertThat(comprobantes).hasSize(1);
        assertThat(comprobantes.get(0)).containsEntry("estado", "ACEPTADO_CON_OBS").containsEntry("intentos", 2);
        assertThat((Map<String, Object>) comprobantes.get(0).get("cdr")).containsEntry("codigo", "0").containsEntry("descripcion", "La Factura ha sido aceptada")
                .containsEntry("observaciones", List.of("4287 - El dato ingresado no cumple"));
        assertThat(((List<Map<String, Object>>) d.get("eventos")).get(0)).containsEntry("comprobante", "F001-00000012").containsEntry("estado_nuevo", "ACEPTADO_CON_OBS");
        assertThat((Map<String, Object>) d.get("outbox")).containsEntry("total", 1);
        assertThat(((List<Map<String, Object>>) ((Map<String, Object>) d.get("outbox")).get("proximas")).get(0)).containsEntry("accion", "ENVIAR").containsEntry("ultimo_error", "SUNAT no responde");
    }

    /** Una API key se ve por su prefijo; ni el secreto que se entregó al crearla ni su hash salen nunca, y tampoco dónde está guardado el logo. */
    @Test void unaApiKeyMuestraSuPrefijoPeroNuncaSuSecretoNiSuHash() {
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String secreto = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        UUID id = idDe("20100066611");
        String hash = jdbc.queryForObject("SELECT key_hash FROM api_key WHERE tenant_id = ?", String.class, id);
        jdbc.update("UPDATE tenant SET pdf_logo_key = 'logos/guardado-en-secreto.png' WHERE id = ?", id);

        String cuerpo = http.exchange("/v1/admin/empresas/" + id, HttpMethod.GET, new HttpEntity<>(conClaveDePlataforma()), String.class).getBody();
        List<Map<String, Object>> keys = (List<Map<String, Object>>) datosDe(abrir(conClaveDePlataforma(), id)).get("api_keys");

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0)).containsEntry("activa", true);
        assertThat(secreto).startsWith((String) keys.get(0).get("prefijo"));
        assertThat(cuerpo).doesNotContain(secreto).doesNotContain(hash).doesNotContain("guardado-en-secreto").doesNotContain("key_hash").doesNotContain("pkcs12");
    }

    @Test void abrirUnaEmpresaQueNoExisteEs404YUnIdMalFormadoEs400() {
        ResponseEntity<Map> noExiste = abrir(conClaveDePlataforma(), UUID.randomUUID());
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noExiste.getBody()).containsEntry("codigo", "NO_ENCONTRADO");

        assertThat(abrir(conClaveDePlataforma(), "no-es-un-uuid").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void unAdministradorConSesionTambienAbreLaEmpresa() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        assertThat(abrir(conBearer(token), idDe("20100066603")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** El detalle trae el domicilio y la situación de una empresa ajena: las mismas puertas que el listado, ninguna más. */
    @Test void nadieMasAbreUnaEmpresa() {
        String jwtDeCliente = clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        UUID propia = idDe("20100066603");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", apiKey);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        assertThat(abrir(json(), propia).getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conBearer(jwtDeCliente), propia).getStatusCode()).as("JWT del propio dueño").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conApiKey, propia).getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conClaveErronea, propia).getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
