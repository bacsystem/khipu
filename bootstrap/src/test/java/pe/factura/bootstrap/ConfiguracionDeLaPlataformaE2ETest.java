package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuración de la plataforma (#199) de extremo a extremo: HTTP real, filtros reales, Postgres real y un correo de mentira que anota lo que sale. Lo que importa: que un texto
 * editado sea el que de verdad sale en un flujo real (recuperar la contraseña, registrarse), que lo inválido no entre ni afecte a lo que ya sale, que el aviso de mantenimiento sea
 * público solo mientras su vigencia lo permita, que cada cambio deje su rastro y que solo la plataforma pueda cambiar la configuración.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ConfiguracionDeLaPlataformaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    static final List<String[]> ENVIADOS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class CorreoDePrueba {
        @Bean @Primary CorreoSender correoQueAnota() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) { ENVIADOS.add(new String[]{para, asunto, cuerpo}); }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new AssertionError("estos correos no llevan adjuntos"); }
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        ENVIADOS.clear();
        jdbc.update("TRUNCATE remitente_correo, plantilla_correo, banner_mantenimiento, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT IF EXISTS ck_prueba_sin_config");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders plataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private ResponseEntity<Map> admin(HttpMethod m, String uri, String cuerpo) { return llamar(m, "/v1/admin/configuracion" + uri, plataforma(), cuerpo); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private long bitacora(String accion) { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = ?", Long.class, accion); }

    private void registrar(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>("{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // --- remitente ----------------------------------------------------------------------------------------------------------------------

    @Test void sinRemitentePropioSeVeElDeLaConfiguracionDelServidor() {
        Map<String, Object> r = datos(admin(HttpMethod.GET, "/correo", null));

        assertThat(r.get("personalizado")).isEqualTo(false);
        assertThat(((Map<String, Object>) r.get("vigente")).get("email")).isEqualTo(((Map<String, Object>) r.get("predeterminado")).get("email"));
        assertThat(r).doesNotContainKey("actualizado_en");
    }

    @Test void cambiarElRemitenteLoGuardaQuedaEnLaBitacoraYSeRestableceAlDelServidor() {
        Map<String, Object> r = datos(admin(HttpMethod.PUT, "/correo", "{\"nombre\":\"khipu\",\"email\":\"avisos@khipu.pe\",\"responder_a\":\"soporte@khipu.pe\"}"));

        assertThat(r.get("personalizado")).isEqualTo(true);
        assertThat((Map<String, Object>) r.get("vigente")).containsEntry("nombre", "khipu").containsEntry("email", "avisos@khipu.pe").containsEntry("responder_a", "soporte@khipu.pe");
        assertThat(datos(admin(HttpMethod.GET, "/correo", null)).get("personalizado")).isEqualTo(true);
        assertThat(bitacora("CAMBIAR_REMITENTE_DE_CORREO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = 'CAMBIAR_REMITENTE_DE_CORREO'", String.class)).contains("khipu <avisos@khipu.pe> responder_a=soporte@khipu.pe");

        Map<String, Object> restablecido = datos(admin(HttpMethod.DELETE, "/correo", null));
        assertThat(restablecido.get("personalizado")).isEqualTo(false);
        assertThat(bitacora("CAMBIAR_REMITENTE_DE_CORREO")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM remitente_correo", Long.class)).isZero();
    }

    @Test void restablecerLoQueNoSeCambioNoDejaRegistro() {
        datos(admin(HttpMethod.DELETE, "/correo", null));

        assertThat(bitacora("CAMBIAR_REMITENTE_DE_CORREO")).isZero();
    }

    @Test void unRemitenteInvalidoEs422NoSeGuardaYNoDejaRegistro() {
        for (String malo : new String[]{"{\"email\":\"no es un correo\"}", "{}", "{\"email\":\"a@khipu.pe\",\"nombre\":\"khipu\\nBcc: x@y.pe\"}", "{\"email\":\"a@khipu.pe, b@khipu.pe\"}"}) {
            ResponseEntity<Map> r = admin(HttpMethod.PUT, "/correo", malo);
            assertThat(r.getStatusCode()).as(malo).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(codigo(r)).isEqualTo("REMITENTE_INVALIDO");
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM remitente_correo", Long.class)).isZero();
        assertThat(bitacora("CAMBIAR_REMITENTE_DE_CORREO")).isZero();
    }

    // --- plantillas ---------------------------------------------------------------------------------------------------------------------

    @Test void lasPlantillasListanTodosLosCorreosConSuTextoDeFabricaYSusVariables() {
        ResponseEntity<Map> r = admin(HttpMethod.GET, "/plantillas", null);

        List<Map<String, Object>> lista = (List<Map<String, Object>>) r.getBody().get("datos");
        assertThat(lista).extracting(p -> p.get("tipo")).containsExactly("VERIFICACION_CORREO", "RECUPERACION_CLAVE", "BIENVENIDA", "AVISO_CERTIFICADO_POR_VENCER", "AVISO_CERTIFICADO_VENCIDO", "AVISO_CREDENCIALES_SOL");
        assertThat(lista).allSatisfy(p -> {
            assertThat(p.get("personalizada")).isEqualTo(false);
            assertThat(p.get("vigente")).isEqualTo(p.get("defecto"));
            assertThat((List<?>) p.get("variables")).isNotEmpty();
        });
    }

    /** El texto editado es el que sale en un flujo real: alguien que olvidó su contraseña recibe lo que el administrador escribió, con su enlace de verdad. */
    @Test void unTextoEditadoEsElQueLlegaEnUnCorreoRealYRestaurarVuelveAlDeFabrica() {
        registrar("ana@negocio.pe");
        ENVIADOS.clear();
        ResponseEntity<Map> guardada = admin(HttpMethod.PUT, "/plantillas/RECUPERACION_CLAVE", "{\"asunto\":\"Recupera tu cuenta de khipu\",\"cuerpo\":\"Hola. Elige una clave nueva aquí ({validez}):\\n{enlace}\\n\\nEl equipo de khipu\"}");
        assertThat(datos(guardada)).containsEntry("personalizada", true);

        assertThat(http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"ana@negocio.pe\"}", json()), Void.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        assertThat(ENVIADOS).hasSize(1);
        assertThat(ENVIADOS.get(0)[0]).isEqualTo("ana@negocio.pe");
        assertThat(ENVIADOS.get(0)[1]).isEqualTo("Recupera tu cuenta de khipu");
        assertThat(ENVIADOS.get(0)[2]).startsWith("Hola. Elige una clave nueva aquí (1 hora):\nhttp").contains("/restablecer/").endsWith("\n\nEl equipo de khipu");

        ResponseEntity<Map> restaurada = admin(HttpMethod.DELETE, "/plantillas/RECUPERACION_CLAVE", null);
        assertThat(datos(restaurada)).containsEntry("personalizada", false);
        ENVIADOS.clear();
        http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"ana@negocio.pe\"}", json()), Void.class);
        assertThat(ENVIADOS.get(0)[1]).isEqualTo("Restablecer contraseña");
        assertThat(ENVIADOS.get(0)[2]).startsWith("Para restablecer tu contraseña abre este enlace (válido 1 hora):");
    }

    @Test void elCorreoDeVerificacionDelRegistroTambienSaleConElTextoEditado() {
        datos(admin(HttpMethod.PUT, "/plantillas/VERIFICACION_CORREO", "{\"asunto\":\"Bienvenida a khipu\",\"cuerpo\":\"Confirma tu correo en {enlace}\"}"));

        registrar("luis@negocio.pe");

        assertThat(ENVIADOS).hasSize(1);
        assertThat(ENVIADOS.get(0)[1]).isEqualTo("Bienvenida a khipu");
        assertThat(ENVIADOS.get(0)[2]).startsWith("Confirma tu correo en http").contains("/verificar/");
    }

    @Test void unaPlantillaSinElEnlaceIndispensableEs422NoSeGuardaYElCorreoSigueSaliendoComoAntes() {
        registrar("ana@negocio.pe");
        ENVIADOS.clear();

        ResponseEntity<Map> r = admin(HttpMethod.PUT, "/plantillas/RECUPERACION_CLAVE", "{\"asunto\":\"Hola\",\"cuerpo\":\"Pide otro enlace en el portal\"}");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(r)).isEqualTo("PLANTILLA_INVALIDA");
        assertThat((String) r.getBody().get("mensaje")).contains("{enlace}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_correo", Long.class)).isZero();
        assertThat(bitacora("EDITAR_PLANTILLA_DE_CORREO")).isZero();
        http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"ana@negocio.pe\"}", json()), Void.class);
        assertThat(ENVIADOS.get(0)[2]).contains("/restablecer/");
    }

    @Test void otrasPlantillasInvalidasTambienSonRechazadas() {
        String[] malas = {
                "{\"asunto\":\"\",\"cuerpo\":\"Entra a {enlace}\"}",
                "{\"asunto\":\"Hola\\nBcc: x@y.pe\",\"cuerpo\":\"Entra a {enlace}\"}",
                "{\"asunto\":\"Hola {ruc}\",\"cuerpo\":\"Entra a {enlace}\"}",
                "{\"asunto\":\"Hola\",\"cuerpo\":\"" + "x".repeat(5001) + " {enlace}\"}",
                "{}"};
        for (String mala : malas) {
            ResponseEntity<Map> r = admin(HttpMethod.PUT, "/plantillas/RECUPERACION_CLAVE", mala);
            assertThat(r.getStatusCode()).as(mala.substring(0, Math.min(60, mala.length()))).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(codigo(r)).isEqualTo("PLANTILLA_INVALIDA");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_correo", Long.class)).isZero();
    }

    @Test void guardarYRestaurarUnaPlantillaQuedanEnLaBitacoraSinElTexto() {
        datos(admin(HttpMethod.PUT, "/plantillas/BIENVENIDA", "{\"asunto\":\"Hola\",\"cuerpo\":\"TEXTO-SECRETO-DEL-CUERPO {enlace}\"}"));
        datos(admin(HttpMethod.DELETE, "/plantillas/BIENVENIDA", null));
        datos(admin(HttpMethod.DELETE, "/plantillas/BIENVENIDA", null));   // ya restaurada: no hace nada

        assertThat(bitacora("EDITAR_PLANTILLA_DE_CORREO")).isEqualTo(1);
        assertThat(bitacora("RESTAURAR_PLANTILLA_DE_CORREO")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT detalle FROM auditoria_admin WHERE accion IN ('EDITAR_PLANTILLA_DE_CORREO', 'RESTAURAR_PLANTILLA_DE_CORREO')", String.class))
                .containsOnly("plantilla=BIENVENIDA");
    }

    @Test void laVistaPreviaRindeElTextoConEjemplosSinGuardarNiDejarRegistro() {
        Map<String, Object> v = datos(admin(HttpMethod.POST, "/plantillas/AVISO_CERTIFICADO_POR_VENCER/vista-previa",
                "{\"asunto\":\"{razon_social} vence {cuando}\",\"cuerpo\":\"Vence el {fecha}. Entra a {enlace}\"}"));

        assertThat(v.get("asunto")).isEqualTo("PANADERIA SOL SAC vence en 10 días");
        assertThat((String) v.get("cuerpo")).startsWith("Vence el 25/10/2026. Entra a http");
        assertThat(admin(HttpMethod.POST, "/plantillas/AVISO_CERTIFICADO_POR_VENCER/vista-previa", "{\"asunto\":\"Hola {nada}\",\"cuerpo\":\"x\"}").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_correo", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Long.class)).isZero();
    }

    @Test void unCorreoQueNoExisteEs404() {
        assertThat(admin(HttpMethod.PUT, "/plantillas/NO_EXISTE", "{\"asunto\":\"a\",\"cuerpo\":\"b\"}").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(admin(HttpMethod.DELETE, "/plantillas/NO_EXISTE", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(admin(HttpMethod.POST, "/plantillas/NO_EXISTE/vista-previa", "{\"asunto\":\"a\",\"cuerpo\":\"b\"}").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- banner -------------------------------------------------------------------------------------------------------------------------

    private ResponseEntity<Map> publico() { return http.getForEntity("/v1/banner", Map.class); }

    private String en(Duration d) { return Instant.now().plus(d).toString(); }

    private String banner(String texto, Duration desde, Duration hasta) { return "{\"texto\":\"%s\",\"desde\":\"%s\",\"hasta\":\"%s\"}".formatted(texto, en(desde), en(hasta)); }

    /** Sin credenciales: lo ve el portal antes de que nadie inicie sesión. */
    @Test void sinBannerElPublicoNoVeNada() {
        ResponseEntity<Map> r = publico();

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("estado")).isEqualTo("exito");
        assertThat(r.getBody()).doesNotContainKey("datos");
        assertThat(r.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test void unBannerVigenteLoVeElPublicoSinCredencialesYAlRetirarloDejaDeVerse() {
        Map<String, Object> publicado = datos(admin(HttpMethod.PUT, "/banner", banner("Mantenimiento esta noche", Duration.ofMinutes(-5), Duration.ofHours(3))));
        assertThat(publicado).containsEntry("texto", "Mantenimiento esta noche").containsEntry("vigente_ahora", true);

        Map<String, Object> visto = (Map<String, Object>) publico().getBody().get("datos");
        assertThat(visto).containsEntry("texto", "Mantenimiento esta noche").containsKeys("desde", "hasta").hasSize(3);

        assertThat(admin(HttpMethod.DELETE, "/banner", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publico().getBody()).doesNotContainKey("datos");
        assertThat(datos(admin(HttpMethod.GET, "/banner", null))).isNull();
    }

    @Test void unBannerProgramadoParaDespuesNoSeMuestraPeroLaPlataformaLoVeComoNoVigente() {
        datos(admin(HttpMethod.PUT, "/banner", banner("Mañana hay mantenimiento", Duration.ofHours(2), Duration.ofHours(6))));

        assertThat(publico().getBody()).doesNotContainKey("datos");
        assertThat(datos(admin(HttpMethod.GET, "/banner", null))).containsEntry("vigente_ahora", false).containsEntry("texto", "Mañana hay mantenimiento");
    }

    @Test void unBannerVencidoDejaDeMostrarseSolo() {
        jdbc.update("INSERT INTO banner_mantenimiento (id, texto, desde, hasta, actualizado_en) VALUES (1, 'Viejo', ?, ?, now())",
                Timestamp.from(Instant.now().minus(Duration.ofHours(5))), Timestamp.from(Instant.now().minus(Duration.ofHours(1))));

        assertThat(publico().getBody()).doesNotContainKey("datos");
        assertThat(datos(admin(HttpMethod.GET, "/banner", null))).containsEntry("vigente_ahora", false);
    }

    @Test void publicarOtroBannerReemplazaAlAnterior() {
        datos(admin(HttpMethod.PUT, "/banner", banner("Uno", Duration.ofMinutes(-5), Duration.ofHours(3))));
        datos(admin(HttpMethod.PUT, "/banner", banner("Dos", Duration.ofMinutes(-5), Duration.ofHours(3))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM banner_mantenimiento", Long.class)).isEqualTo(1);
        assertThat(((Map<String, Object>) publico().getBody().get("datos")).get("texto")).isEqualTo("Dos");
    }

    @Test void unBannerInvalidoEs422NoSeGuardaYNoDejaRegistro() {
        String[] malos = {
                banner("", Duration.ofMinutes(-5), Duration.ofHours(3)),
                banner("x", Duration.ofHours(3), Duration.ofMinutes(-5)),
                banner("x", Duration.ofHours(-5), Duration.ofHours(-1)),
                banner("x", Duration.ofMinutes(-5), Duration.ofDays(91)),
                banner("a\\nb", Duration.ofMinutes(-5), Duration.ofHours(3)),
                "{\"texto\":\"x\"}", "{}"};
        for (String malo : malos) {
            ResponseEntity<Map> r = admin(HttpMethod.PUT, "/banner", malo);
            assertThat(r.getStatusCode()).as(malo).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(codigo(r)).isEqualTo("BANNER_INVALIDO");
        }
        assertThat(admin(HttpMethod.PUT, "/banner", "{\"texto\":\"x\",\"desde\":\"mañana\",\"hasta\":\"2026-10-16T01:00:00Z\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM banner_mantenimiento", Long.class)).isZero();
        assertThat(bitacora("PUBLICAR_BANNER")).isZero();
    }

    @Test void publicarYRetirarUnBannerQuedanEnLaBitacoraYRetirarSinBannerEs404() {
        assertThat(admin(HttpMethod.DELETE, "/banner", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bitacora("RETIRAR_BANNER")).isZero();

        datos(admin(HttpMethod.PUT, "/banner", banner("Mantenimiento esta noche", Duration.ofMinutes(-5), Duration.ofHours(3))));
        assertThat(admin(HttpMethod.DELETE, "/banner", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(bitacora("PUBLICAR_BANNER")).isEqualTo(1);
        assertThat(bitacora("RETIRAR_BANNER")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = 'PUBLICAR_BANNER'", String.class)).startsWith("texto=Mantenimiento esta noche desde=").contains(" hasta=");
    }

    /** Si la bitácora no puede escribir, el cambio no queda: el cambio y su registro son una sola transacción. */
    @Test void siLaBitacoraFallaElCambioNoQueda() {
        jdbc.update("ALTER TABLE auditoria_admin ADD CONSTRAINT ck_prueba_sin_config CHECK (accion NOT IN ('PUBLICAR_BANNER', 'EDITAR_PLANTILLA_DE_CORREO', 'CAMBIAR_REMITENTE_DE_CORREO'))");
        try {
            assertThat(admin(HttpMethod.PUT, "/banner", banner("x", Duration.ofMinutes(-5), Duration.ofHours(3))).getStatusCode().is5xxServerError()).isTrue();
            assertThat(admin(HttpMethod.PUT, "/plantillas/BIENVENIDA", "{\"asunto\":\"Hola\",\"cuerpo\":\"Entra a {enlace}\"}").getStatusCode().is5xxServerError()).isTrue();
            assertThat(admin(HttpMethod.PUT, "/correo", "{\"email\":\"a@khipu.pe\"}").getStatusCode().is5xxServerError()).isTrue();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM banner_mantenimiento", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_correo", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM remitente_correo", Long.class)).isZero();
        } finally {
            jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT ck_prueba_sin_config");
        }
    }

    // --- quién lo cambia ----------------------------------------------------------------------------------------------------------------

    /** Cambiar el remitente o los textos de los correos afecta a todos los clientes: solo la plataforma. El aviso público, en cambio, lo ve cualquiera. */
    @Test void soloLaPlataformaPuedeCambiarLaConfiguracionPeroElAvisoLoVeTodo() {
        registrar("ana@negocio.pe");
        String access = (String) ((Map<?, ?>) http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}", json()), Map.class).getBody().get("datos")).get("access");
        HttpHeaders delCliente = json(); delCliente.setBearerAuth(access);
        HttpHeaders claveErronea = json(); claveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), delCliente, claveErronea)) {
            for (String[] ruta : new String[][]{{"GET", "/correo"}, {"PUT", "/correo"}, {"DELETE", "/correo"}, {"GET", "/plantillas"}, {"PUT", "/plantillas/BIENVENIDA"}, {"DELETE", "/plantillas/BIENVENIDA"},
                    {"POST", "/plantillas/BIENVENIDA/vista-previa"}, {"GET", "/banner"}, {"PUT", "/banner"}, {"DELETE", "/banner"}}) {
                ResponseEntity<Map> r = llamar(HttpMethod.valueOf(ruta[0]), "/v1/admin/configuracion" + ruta[1], h, "{}");
                assertThat(r.getStatusCode()).as("%s %s con %s", ruta[0], ruta[1], h).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM banner_mantenimiento", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM plantilla_correo", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM remitente_correo", Long.class)).isZero();

        assertThat(publico().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/v1/banner", HttpMethod.GET, new HttpEntity<>(delCliente), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Solo esa ruta es pública: lo que cuelga de un prefijo parecido sigue pidiendo credenciales. */
    @Test void soloElAvisoEsPublicoNoLoQueSeLeParece() {
        assertThat(http.getForEntity("/v1/banner/otra", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/v1/banners", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
