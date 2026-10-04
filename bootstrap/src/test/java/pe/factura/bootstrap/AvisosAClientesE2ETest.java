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
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.plan.CicloMensual;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los avisos a los clientes (#197) de extremo a extremo: HTTP real, filtros reales, Postgres real y un correo de mentira que anota lo que sale. Lo que importa: que las listas
 * digan lo mismo que el dominio (certificado en riesgo, SOL rechazada), que solo se avise lo que es cierto y a quien tiene cuenta, que el mismo aviso no se repita ni aunque dos
 * administradores hagan clic a la vez, que un correo que falla no deje un aviso fantasma, y que solo la plataforma lo use.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AvisosAClientesE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** El correo de mentira: anota lo que salió, puede fallar o decir que no entrega (sin SMTP). */
    static final List<String[]> ENVIADOS = new CopyOnWriteArrayList<>();
    static final AtomicBoolean FALLA = new AtomicBoolean();
    static final AtomicBoolean ENTREGA = new AtomicBoolean(true);

    @TestConfiguration
    static class CorreoDePrueba {
        @Bean @Primary CorreoSender correoQueAnota() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) {
                    if (FALLA.get()) throw new IllegalStateException("SMTP caído");
                    ENVIADOS.add(new String[]{para, asunto, cuerpo});
                }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new AssertionError("los avisos no llevan adjuntos"); }
                public boolean entregaDeVerdad() { return ENTREGA.get(); }
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final LocalDate HOY = LocalDate.now(CicloMensual.ZONA);
    static final String[] RUCS = {"20100066603", "20100066611", "20100066620", "20100066638"};
    static final AtomicLong NUMERO = new AtomicLong(1);

    @BeforeEach void limpiar() {
        ENVIADOS.clear();
        FALLA.set(false);
        ENTREGA.set(true);
        jdbc.update("TRUNCATE aviso_a_cliente, outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT IF EXISTS ck_prueba_sin_avisos");
    }

    record Cliente(String access, UUID cuentaId, UUID empresaId, String apiKey, String ruc, String email) {}

    private int siguiente = 0;

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    /** Un cliente real con su cuenta y su empresa; el certificado vence {@code dias} días después de hoy ({@code null}: sin certificado). */
    private Cliente cliente(String email, Integer dias) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = RUCS[siguiente++];
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(access);
        h.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        if (dias != null) vence(empresa, dias);
        ENVIADOS.clear();   // el registro manda su propio correo de verificación: acá solo importan los avisos
        return new Cliente(access, jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email), empresa, key, ruc, email);
    }

    private void vence(UUID empresa, int dias) {
        jdbc.update("UPDATE tenant SET cert_pkcs12_enc = '\\x01'::bytea, cert_vigencia_hasta = ? WHERE id = ?", Date.valueOf(HOY.plusDays(dias)), empresa);
    }

    /** Una empresa de integración: sin cuenta. */
    private UUID integrada(int dias) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, ?, 'INTEGRADA SAC', 'BETA')", id, RUCS[siguiente++]);
        vence(id, dias);
        return id;
    }

    private void envioConError(UUID empresa, String estado, String error) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, ultimo_error) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'x', ?)",
                UUID.randomUUID(), empresa, NUMERO.getAndIncrement(), Date.valueOf(HOY), estado, error);
    }

    private ResponseEntity<Map> certificados() { return llamar(HttpMethod.GET, "/v1/admin/avisos/certificados", conClaveDePlataforma(), null); }

    private ResponseEntity<Map> credencialesSol() { return llamar(HttpMethod.GET, "/v1/admin/avisos/credenciales-sol", conClaveDePlataforma(), null); }

    private ResponseEntity<Map> avisar(UUID empresa, String tipo) { return llamar(HttpMethod.POST, "/v1/admin/empresas/" + empresa + "/avisos", conClaveDePlataforma(), "{\"tipo\":\"%s\"}".formatted(tipo)); }

    private List<Map<String, Object>> filas(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) r.getBody().get("datos");
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private long avisos() { return jdbc.queryForObject("SELECT count(*) FROM aviso_a_cliente", Long.class); }

    private long bitacora() { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'AVISAR_AL_CLIENTE'", Long.class); }

    // --- las listas -------------------------------------------------------------------------------------------------------------------

    @Test void laListaDeCertificadosTraeSoloLosEnRiesgoDeLaMasUrgenteALaMenos() {
        Cliente vencido = cliente("a@negocio.pe", -5);
        Cliente cerca = cliente("b@negocio.pe", 3);
        Cliente lejos = cliente("c@negocio.pe", 25);
        cliente("d@negocio.pe", 30);

        ResponseEntity<Map> r = certificados();
        List<Map<String, Object>> filas = filas(r);

        assertThat(filas).extracting(f -> f.get("empresa_id")).containsExactly(vencido.empresaId().toString(), cerca.empresaId().toString(), lejos.empresaId().toString());
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("3");
        assertThat(filas.get(0)).containsEntry("motivo", "CERTIFICADO_VENCIDO").containsEntry("dias_restantes", -5).containsEntry("puede_avisar", true);
        assertThat(filas.get(1)).containsEntry("motivo", "CERTIFICADO_POR_VENCER").containsEntry("dias_restantes", 3).containsEntry("vigente_hasta", HOY.plusDays(3).toString());
        assertThat((Map<String, Object>) filas.get(0).get("cuenta")).containsEntry("email", "a@negocio.pe").containsEntry("id", vencido.cuentaId().toString());
        assertThat(filas.get(0)).doesNotContainKeys("ultimo_aviso", "avisar_desde");
    }

    @Test void unaEmpresaDeIntegracionApareceSinCuentaYNoSePuedeAvisar() {
        UUID integrada = integrada(-2);

        Map<String, Object> f = filas(certificados()).get(0);

        assertThat(f).containsEntry("empresa_id", integrada.toString()).containsEntry("puede_avisar", false).doesNotContainKey("cuenta");
    }

    @Test void unaCuentaDadaDeBajaYaNoSeAvisa() {
        Cliente c = cliente("a@negocio.pe", -5);
        jdbc.update("UPDATE cuenta SET baja_en = now() WHERE id = ?", c.cuentaId());

        assertThat(filas(certificados())).isEmpty();
    }

    @Test void laListaDeSolTraeLasEmpresasConEnviosAtascadosPorLasCredencialesYSaleSolaAlResolverse() {
        Cliente sol = cliente("a@negocio.pe", 200);
        Cliente caida = cliente("b@negocio.pe", 200);
        envioConError(sol.empresaId(), "ERROR_ENVIO", "0102 - Usuario o contraseña incorrectos");
        envioConError(sol.empresaId(), "ERROR_ENVIO", "0000 - SUNAT respondió HTTP 401 en 2 intentos");
        envioConError(caida.empresaId(), "ERROR_ENVIO", "0109 - El sistema no puede responder");

        ResponseEntity<Map> r = credencialesSol();
        List<Map<String, Object>> filas = filas(r);

        assertThat(filas).hasSize(1);
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(filas.get(0)).containsEntry("empresa_id", sol.empresaId().toString()).containsEntry("comprobantes_afectados", 2).containsEntry("puede_avisar", true).containsKey("ultimo_fallo")
                .containsKey("ultimo_error");

        jdbc.update("UPDATE documento SET estado = 'ACEPTADO' WHERE tenant_id = ?", sol.empresaId());

        assertThat(filas(credencialesSol())).isEmpty();
    }

    @Test void laListaDeSolPagina() {
        for (int i = 0; i < 3; i++) envioConError(cliente("c" + i + "@negocio.pe", 200).empresaId(), "ERROR_ENVIO", "0102 - x");

        assertThat(filas(llamar(HttpMethod.GET, "/v1/admin/avisos/credenciales-sol?por_pagina=2&pagina=2", conClaveDePlataforma(), null))).hasSize(1);
    }

    // --- avisar -----------------------------------------------------------------------------------------------------------------------

    @Test void avisarDelCertificadoMandaElCorreoRegistraElAvisoYLoDejaEnLaBitacoraSinElCorreo() {
        Cliente c = cliente("ana@negocio.pe", 12);

        ResponseEntity<Map> r = avisar(c.empresaId(), "CERTIFICADO");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = (Map<String, Object>) r.getBody().get("datos");
        assertThat(d).containsEntry("motivo", "CERTIFICADO_POR_VENCER").containsEntry("destinatario", "ana@negocio.pe").containsKey("enviado_en").containsKey("avisar_desde");
        assertThat(ENVIADOS).hasSize(1);
        assertThat(ENVIADOS.get(0)[0]).isEqualTo("ana@negocio.pe");
        assertThat(ENVIADOS.get(0)[1]).isEqualTo("Tu certificado digital de EMPRESA " + c.ruc() + " vence en 12 días");
        assertThat(ENVIADOS.get(0)[2]).contains(c.ruc()).contains("http://localhost:3000");
        Map<String, Object> reg = jdbc.queryForMap("SELECT tenant_id, cuenta_id, motivo, destinatario, enviado_por FROM aviso_a_cliente");
        assertThat(reg.get("tenant_id")).isEqualTo(c.empresaId());
        assertThat(reg.get("cuenta_id")).isEqualTo(c.cuentaId());
        assertThat(reg.get("motivo")).isEqualTo("CERTIFICADO_POR_VENCER");
        assertThat(reg.get("enviado_por")).isNull();
        Map<String, Object> bit = jdbc.queryForMap("SELECT actor_tipo, cuenta_id, tenant_id, detalle FROM auditoria_admin WHERE accion = 'AVISAR_AL_CLIENTE'");
        assertThat(bit.get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(bit.get("cuenta_id")).isEqualTo(c.cuentaId());
        assertThat(bit.get("tenant_id")).isEqualTo(c.empresaId());
        assertThat(bit.get("detalle")).isEqualTo("motivo=CERTIFICADO_POR_VENCER");
    }

    @Test void avisarDeLasCredencialesSolManda_elCorreoDeLasCredenciales() {
        Cliente c = cliente("ana@negocio.pe", 200);
        envioConError(c.empresaId(), "ERROR_ENVIO", "0102 - Usuario o contraseña incorrectos");

        ResponseEntity<Map> r = avisar(c.empresaId(), "CREDENCIALES_SOL");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ENVIADOS.get(0)[1]).isEqualTo("SUNAT no acepta las credenciales SOL de EMPRESA " + c.ruc());
        assertThat(jdbc.queryForObject("SELECT motivo FROM aviso_a_cliente", String.class)).isEqualTo("CREDENCIALES_SOL_INVALIDAS");
    }

    @Test void despuesDeAvisarLaListaLoDiceYBloqueaElBoton() {
        Cliente c = cliente("ana@negocio.pe", 12);
        avisar(c.empresaId(), "CERTIFICADO");

        Map<String, Object> f = filas(certificados()).get(0);

        assertThat(f).containsEntry("puede_avisar", false).containsKey("avisar_desde");
        assertThat((Map<String, Object>) f.get("ultimo_aviso")).containsEntry("destinatario", "ana@negocio.pe").containsKey("enviado_en");
    }

    @Test void elMismoAvisoNoSeRepiteNiSeManda_unSegundoCorreo() {
        Cliente c = cliente("ana@negocio.pe", 12);
        avisar(c.empresaId(), "CERTIFICADO");

        ResponseEntity<Map> otra = avisar(c.empresaId(), "CERTIFICADO");

        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(otra)).isEqualTo("AVISO_RECIENTE");
        assertThat(ENVIADOS).hasSize(1);
        assertThat(avisos()).isEqualTo(1);
        assertThat(bitacora()).isEqualTo(1);
    }

    @Test void pasarDePorVencerAVencidoEsUnAvisoNuevo() {
        Cliente c = cliente("ana@negocio.pe", 2);
        avisar(c.empresaId(), "CERTIFICADO");
        vence(c.empresaId(), -1);

        ResponseEntity<Map> r = avisar(c.empresaId(), "CERTIFICADO");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ENVIADOS).hasSize(2);
        assertThat(ENVIADOS.get(1)[1]).isEqualTo("El certificado digital de EMPRESA " + c.ruc() + " venció");
        assertThat(avisos()).isEqualTo(2);
    }

    @Test void despuesDeUnaSemanaSePuedeRepetir() {
        Cliente c = cliente("ana@negocio.pe", 12);
        avisar(c.empresaId(), "CERTIFICADO");
        jdbc.update("UPDATE aviso_a_cliente SET enviado_en = now() - interval '7 days'");

        assertThat(avisar(c.empresaId(), "CERTIFICADO").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ENVIADOS).hasSize(2);
    }

    @Test void soloSeAvisaLoQueEsCierto() {
        Cliente vigente = cliente("a@negocio.pe", 200);
        Cliente sinCert = cliente("b@negocio.pe", null);
        Cliente solBien = cliente("c@negocio.pe", -3);

        for (UUID e : List.of(vigente.empresaId(), sinCert.empresaId())) {
            ResponseEntity<Map> r = avisar(e, "CERTIFICADO");
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(codigo(r)).isEqualTo("AVISO_SIN_MOTIVO");
        }
        ResponseEntity<Map> sol = avisar(solBien.empresaId(), "CREDENCIALES_SOL");
        assertThat(sol.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(sol)).isEqualTo("AVISO_SIN_MOTIVO");

        assertThat(ENVIADOS).isEmpty();
        assertThat(avisos()).isZero();
        assertThat(bitacora()).isZero();
    }

    @Test void unaEmpresaDeIntegracionNoTieneAQuienAvisarle() {
        UUID integrada = integrada(-2);

        ResponseEntity<Map> r = avisar(integrada, "CERTIFICADO");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("EMPRESA_SIN_CUENTA");
        assertThat(ENVIADOS).isEmpty();
    }

    @Test void unaEmpresaQueNoExisteEs404YSinTipoEs422() {
        assertThat(avisar(UUID.randomUUID(), "CERTIFICADO").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Cliente c = cliente("ana@negocio.pe", 12);
        ResponseEntity<Map> sinTipo = llamar(HttpMethod.POST, "/v1/admin/empresas/" + c.empresaId() + "/avisos", conClaveDePlataforma(), "{}");
        assertThat(sinTipo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(sinTipo)).isEqualTo("TIPO_INVALIDO");
        assertThat(llamar(HttpMethod.POST, "/v1/admin/empresas/" + c.empresaId() + "/avisos", conClaveDePlataforma(), "{\"tipo\":\"OTRO\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- el correo ---------------------------------------------------------------------------------------------------------------------

    @Test void sinSmtpNoSeMandaNadaNiSeRegistraNada() {
        Cliente c = cliente("ana@negocio.pe", 12);
        ENTREGA.set(false);

        ResponseEntity<Map> r = avisar(c.empresaId(), "CERTIFICADO");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(codigo(r)).isEqualTo("CORREO_NO_CONFIGURADO");
        assertThat(avisos()).isZero();
        assertThat(bitacora()).isZero();
    }

    @Test void siElCorreoFallaNoQuedaUnAvisoFantasmaYSePuedeReintentar() {
        Cliente c = cliente("ana@negocio.pe", 12);
        FALLA.set(true);

        ResponseEntity<Map> r = avisar(c.empresaId(), "CERTIFICADO");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(codigo(r)).isEqualTo("CORREO_NO_ENVIADO");
        assertThat(avisos()).isZero();
        assertThat(bitacora()).isZero();

        FALLA.set(false);
        assertThat(avisar(c.empresaId(), "CERTIFICADO").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ENVIADOS).hasSize(1);
        assertThat(avisos()).isEqualTo(1);
    }

    /** Si la bitácora no puede escribir, el correo ya salió: el aviso queda reservado para que un reintento no mande otro. */
    @Test void siLaBitacoraFallaElAvisoQuedaReservadoYNoSeDuplica() {
        Cliente c = cliente("ana@negocio.pe", 12);
        jdbc.update("ALTER TABLE auditoria_admin ADD CONSTRAINT ck_prueba_sin_avisos CHECK (accion <> 'AVISAR_AL_CLIENTE')");
        try {
            ResponseEntity<Map> r = avisar(c.empresaId(), "CERTIFICADO");
            assertThat(r.getStatusCode().is5xxServerError()).isTrue();
            assertThat(ENVIADOS).hasSize(1);
            assertThat(avisos()).isEqualTo(1);

            assertThat(codigo(avisar(c.empresaId(), "CERTIFICADO"))).isEqualTo("AVISO_RECIENTE");
            assertThat(ENVIADOS).hasSize(1);
        } finally {
            jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT ck_prueba_sin_avisos");
        }
    }

    /** Dos administradores que avisan a la vez lo mismo: un correo y un registro; el otro recibe un 409. Nunca un 500 ni dos correos. */
    @Test void dosAvisosAlMismoTiempoMandanUnSoloCorreo() throws Exception {
        Cliente c = cliente("ana@negocio.pe", 12);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<Map>>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<ResponseEntity<Map>> tarea = () -> { salida.await(); return avisar(c.empresaId(), "CERTIFICADO"); };
                futuros.add(pool.submit(tarea));
            }
            salida.countDown();
            List<HttpStatusCode> estados = new ArrayList<>();
            for (Future<ResponseEntity<Map>> f : futuros) estados.add(f.get().getStatusCode());

            assertThat(estados).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            assertThat(ENVIADOS).hasSize(1);
            assertThat(avisos()).isEqualTo(1);
            assertThat(bitacora()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- quién lo ve ------------------------------------------------------------------------------------------------------------------------

    /** Los avisos mandan correos a clientes ajenos: ni el dueño, ni una API key, ni una clave errónea pueden verlos ni mandarlos. */
    @Test void soloLaPlataformaPuedeVerLosAvisosNiMandarlos() {
        Cliente c = cliente("ana@negocio.pe", 12);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/avisos/certificados", h, null).getStatusCode()).as("certificados con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/avisos/credenciales-sol", h, null).getStatusCode()).as("sol con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/empresas/" + c.empresaId() + "/avisos", h, "{\"tipo\":\"CERTIFICADO\"}").getStatusCode()).as("avisar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(ENVIADOS).isEmpty();
        assertThat(avisos()).isZero();
    }
}
