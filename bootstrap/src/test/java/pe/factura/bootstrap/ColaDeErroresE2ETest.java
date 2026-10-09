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
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.application.port.out.DocumentStorage;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La cola global de errores (#196) de extremo a extremo: HTTP real, filtros reales, Postgres real y una SUNAT de mentira que contesta lo que se le diga. Lo que importa: que la
 * cola junte los errores de **todas** las empresas con su fault y sus intentos, que reintentar respete las reglas de estado (un rechazo o un descartado no se reenvía), que
 * descartar saque el envío del outbox y deje la bitácora en la misma transacción, que dos administradores a la vez no se pisen, y que solo la plataforma lo vea.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ColaDeErroresE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Qué contesta SUNAT al envío: por defecto no responde (error de envío); se puede hacer que rechace con un fault. */
    static final AtomicReference<RuntimeException> FALLA = new AtomicReference<>();
    static final AtomicInteger ENVIOS = new AtomicInteger();

    @TestConfiguration
    static class SunatDePrueba {
        @Bean @Primary SunatBillingGateway sunatQueContesta() {
            return new SunatBillingGateway() {
                public byte[] sendBill(Tenant t, String nombreArchivo, byte[] xml) {
                    ENVIOS.incrementAndGet();
                    throw FALLA.get() != null ? FALLA.get() : new SunatTransientException("0109", "El sistema no puede responder su solicitud");
                }
                public String sendSummary(Tenant t, String nombreArchivo, byte[] xml) { throw new AssertionError("la cola no envía resúmenes"); }
                public EstadoTicket getStatus(Tenant t, String ticket) { throw new AssertionError("la cola no consulta tickets"); }
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ComprobanteRepository comprobantes;
    @Autowired DocumentStorage storage;

    static final AtomicLong NUMERO = new AtomicLong(1);
    static final String[] RUCS = {"20100066603", "20100066611", "20100066620"};
    static final LocalDate HOY = LocalDate.now(CicloMensual.ZONA);

    @BeforeEach void limpiar() {
        FALLA.set(null);
        ENVIOS.set(0);
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT IF EXISTS ck_prueba_sin_descartes");
    }

    record Cliente(String access, UUID cuentaId, UUID empresaId, String apiKey, String ruc) {}

    private int siguiente = 0;

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    /** Un cliente real: su cuenta, su empresa con credenciales SOL y una API key. */
    private Cliente cliente(String email) {
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
        assertThat(llamar(HttpMethod.PUT, "/v1/empresa/credenciales-sol", conApiKey(key), "{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}").getStatusCode().is2xxSuccessful()).isTrue();
        return new Cliente(access, jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email), empresa, key, ruc);
    }

    /** Un comprobante de la empresa firmado, con su XML guardado, que ya falló {@code fallos} veces al enviarse, y su tarea en el outbox. */
    private Comprobante enError(Cliente c, int fallos) {
        Comprobante f = Comprobante.factura(c.empresaId(), "F001", HOY, "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(Clock.system(CicloMensual.ZONA));
        f.asignarNumero(NUMERO.getAndIncrement(), c.ruc());
        String key = "e2e-cola/" + f.nombreArchivo() + ".xml";
        storage.guardar(key, "<xml/>".getBytes());
        f.firmar("hash", key);
        comprobantes.guardar(f);
        for (int i = 0; i < fallos; i++) {
            if (i > 0) f.marcarEnviado();
            f.marcarErrorEnvio("0109 - El sistema no puede responder su solicitud");
            comprobantes.guardar(f);
        }
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, 'ENVIAR', now() + interval '1 hour')", c.empresaId(), f.id());
        return f;
    }

    private void rechazadoPorFault(Cliente c, String codigo) {
        Comprobante f = enError(c, 1);
        f.rechazarPorFault(codigo, "El comprobante fue registrado previamente con otros datos");
        comprobantes.guardar(f);
        jdbc.update("DELETE FROM outbox WHERE agregado_id = ?", f.id());
    }

    private ResponseEntity<Map> cola(String query) { return llamar(HttpMethod.GET, "/v1/admin/errores" + query, conClaveDePlataforma(), null); }

    private List<Map<String, Object>> filas(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) r.getBody().get("datos");
    }

    private ResponseEntity<Map> reintentar(UUID id) { return llamar(HttpMethod.POST, "/v1/admin/comprobantes/" + id + "/reintento", conClaveDePlataforma(), null); }

    private ResponseEntity<Map> descartar(UUID id, String motivo) {
        return llamar(HttpMethod.POST, "/v1/admin/comprobantes/" + id + "/descarte", conClaveDePlataforma(), motivo == null ? "{}" : "{\"motivo\":\"%s\"}".formatted(motivo));
    }

    private String estado(UUID id) { return jdbc.queryForObject("SELECT estado FROM documento WHERE id = ?", String.class, id); }

    private long enOutbox(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?", Long.class, id); }

    private long bitacora(String accion) { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = ?", Long.class, accion); }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    // --- la cola -----------------------------------------------------------------------------------------------------------------------------

    @Test void juntaLosErroresDeTodasLasEmpresasConSuFaultYSusIntentos() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@negocio.pe");
        Comprobante deA = enError(a, 2);
        Comprobante deB = enError(b, 1);
        rechazadoPorFault(b, "1033");
        Comprobante aceptado = enError(a, 1);
        jdbc.update("UPDATE documento SET estado = 'ACEPTADO' WHERE id = ?", aceptado.id());
        rechazadoPorFault(a, "2324");   // un rechazo de validación (2000–3999) no es un error de formato: no entra

        ResponseEntity<Map> r = cola("");
        List<Map<String, Object>> filas = filas(r);

        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("3");
        assertThat(filas).hasSize(3);
        Map<String, Object> fa = filas.stream().filter(f -> f.get("comprobante_id").equals(deA.id().toString())).findFirst().orElseThrow();
        assertThat(fa).containsEntry("clase", "ERROR_DE_ENVIO").containsEntry("estado", "ERROR_ENVIO").containsEntry("intentos", 2).containsEntry("accionable", true)
                .containsEntry("ruc", a.ruc()).containsEntry("razon_social", "EMPRESA " + a.ruc()).containsEntry("empresa_id", a.empresaId().toString())
                .containsEntry("cuenta_id", a.cuentaId().toString()).containsEntry("cuenta_nombre", "Mi negocio").containsEntry("nombre_archivo", deA.nombreArchivo())
                .containsKey("proximo_intento");
        assertThat((Map<String, Object>) fa.get("fault")).containsEntry("codigo", "0109").containsEntry("mensaje", "El sistema no puede responder su solicitud");
        Map<String, Object> formato = filas.stream().filter(f -> "ERROR_DE_FORMATO".equals(f.get("clase"))).findFirst().orElseThrow();
        assertThat(formato).containsEntry("accionable", false).containsEntry("estado", "RECHAZADO").doesNotContainKey("proximo_intento");
        assertThat((Map<String, Object>) formato.get("fault")).containsEntry("codigo", "1033");
    }

    @Test void filtraPorClasePorEmpresaYPorCliente() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@negocio.pe");
        enError(a, 1);
        rechazadoPorFault(a, "1001");
        enError(b, 1);

        assertThat(filas(cola("?clase=ERROR_DE_ENVIO"))).hasSize(2);
        assertThat(filas(cola("?clase=ERROR_DE_FORMATO"))).hasSize(1);
        assertThat(filas(cola("?clase=FUERA_DE_PLAZO"))).isEmpty();
        assertThat(filas(cola("?empresa_id=" + a.empresaId()))).hasSize(2).allSatisfy(f -> assertThat(f.get("empresa_id")).isEqualTo(a.empresaId().toString()));
        assertThat(filas(cola("?q=" + b.ruc()))).hasSize(1);
        assertThat(filas(cola("?q=beto@negocio"))).hasSize(1);
        assertThat(filas(cola("?clase=ERROR_DE_ENVIO&empresa_id=" + a.empresaId() + "&q=" + a.ruc()))).hasSize(1);
        assertThat(cola("?clase=ERROR_DE_ENVIO").getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
    }

    @Test void pagina() {
        Cliente a = cliente("ana@negocio.pe");
        for (int i = 0; i < 5; i++) enError(a, 1);

        assertThat(filas(cola("?por_pagina=2&pagina=3"))).hasSize(1);
        assertThat(cola("?por_pagina=2&pagina=1").getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
    }

    @Test void unaClaseMalEscritaEs400() {
        ResponseEntity<Map> r = cola("?clase=OTRA");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(r)).isEqualTo("PARAMETRO_INVALIDO");
    }

    // --- reintentar --------------------------------------------------------------------------------------------------------------------------

    @Test void siSunatVuelveAFallarElReintentoDiceElFaultSumaUnIntentoYDejaLaBitacora() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 2);

        Map<String, Object> d = datos(reintentar(c.id()));

        assertThat(ENVIOS.get()).isEqualTo(1);
        assertThat(d).containsEntry("estado", "ERROR_ENVIO").containsEntry("intentos", 3);
        assertThat((Map<String, Object>) d.get("fault")).containsEntry("codigo", "0109");
        assertThat(estado(c.id())).isEqualTo("ERROR_ENVIO");
        assertThat(jdbc.queryForObject("SELECT intentos FROM documento WHERE id = ?", Integer.class, c.id())).isEqualTo(3);
        assertThat(enOutbox(c.id())).as("sigue programado para reintentarse solo").isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, cuenta_id, tenant_id, detalle FROM auditoria_admin WHERE accion = 'REINTENTAR_ENVIO_COMPROBANTE'");
        assertThat(fila.get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(fila.get("cuenta_id")).isEqualTo(a.cuentaId());
        assertThat(fila.get("tenant_id")).isEqualTo(a.empresaId());
        assertThat(fila.get("detalle")).isEqualTo("comprobante=" + c.nombreArchivo() + " resultado=ERROR_ENVIO");
    }

    @Test void unFaultDeFormatoDeSunatRechazaYElComprobanteSaleDeLosReintentablesYEntraComoErrorDeFormato() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        FALLA.set(new SunatRechazoException("1033", "El comprobante fue registrado previamente con otros datos"));

        Map<String, Object> d = datos(reintentar(c.id()));

        assertThat(d).containsEntry("estado", "RECHAZADO");
        assertThat((Map<String, Object>) d.get("fault")).containsEntry("codigo", "1033");
        Map<String, Object> fila = filas(cola("")).get(0);
        assertThat(fila).containsEntry("clase", "ERROR_DE_FORMATO").containsEntry("accionable", false);
        assertThat(bitacora("REINTENTAR_ENVIO_COMPROBANTE")).isEqualTo(1);
    }

    @Test void reintentarRespetaLasReglasDeEstadoYNoEnviaLoQueNoSePuedeEnviar() {
        Cliente a = cliente("ana@negocio.pe");
        rechazadoPorFault(a, "1033");
        UUID rechazado = jdbc.queryForObject("SELECT id FROM documento WHERE estado = 'RECHAZADO'", UUID.class);

        ResponseEntity<Map> r = reintentar(rechazado);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("ESTADO_NO_ENVIABLE");
        assertThat(ENVIOS.get()).as("SUNAT ni se enteró").isZero();
        assertThat(estado(rechazado)).isEqualTo("RECHAZADO");
        assertThat(bitacora("REINTENTAR_ENVIO_COMPROBANTE")).as("el intento que no se pudo hacer también queda anotado").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = 'REINTENTAR_ENVIO_COMPROBANTE'", String.class)).endsWith("no se pudo=ESTADO_NO_ENVIABLE");
    }

    @Test void siSePasoElPlazoElReintentoNoEnviaYElComprobanteQuedaFueraDePlazo() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        jdbc.update("UPDATE documento SET fecha_emision = ? WHERE id = ?", Date.valueOf(HOY.minusDays(10)), c.id());

        ResponseEntity<Map> r = reintentar(c.id());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("FUERA_DE_PLAZO");
        assertThat(ENVIOS.get()).isZero();
        assertThat(estado(c.id())).isEqualTo("FUERA_DE_PLAZO");
        assertThat(filas(cola("")).get(0)).containsEntry("clase", "FUERA_DE_PLAZO").containsEntry("accionable", false);
    }

    @Test void reintentarUnComprobanteQueNoExisteEs404SinAnotarNada() {
        ResponseEntity<Map> r = reintentar(UUID.randomUUID());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bitacora("REINTENTAR_ENVIO_COMPROBANTE")).isZero();
    }

    // --- descartar ---------------------------------------------------------------------------------------------------------------------------

    @Test void descartarDejaElComprobanteTerminalLoSacaDelOutboxYDeLaColaYDejaLaBitacora() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 3);
        Comprobante otro = enError(a, 1);

        Map<String, Object> d = datos(descartar(c.id(), "El cliente lo reemitió con otra serie"));

        assertThat(d).containsEntry("estado", "DESCARTADO");
        assertThat(estado(c.id())).isEqualTo("DESCARTADO");
        assertThat(enOutbox(c.id())).as("ya no se reintenta").isZero();
        assertThat(enOutbox(otro.id())).as("el de otro comprobante sigue").isEqualTo(1);
        assertThat(filas(cola(""))).extracting(f -> f.get("comprobante_id")).containsExactly(otro.id().toString());
        assertThat(jdbc.queryForObject("SELECT intentos FROM documento WHERE id = ?", Integer.class, c.id())).as("los intentos que hubo no se borran").isEqualTo(3);
        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, cuenta_id, tenant_id, detalle FROM auditoria_admin WHERE accion = 'DESCARTAR_COMPROBANTE'");
        assertThat(fila.get("cuenta_id")).isEqualTo(a.cuentaId());
        assertThat(fila.get("tenant_id")).isEqualTo(a.empresaId());
        assertThat(fila.get("detalle")).isEqualTo("comprobante=" + c.nombreArchivo() + " motivo=El cliente lo reemitió con otra serie");
        assertThat(jdbc.queryForObject("SELECT detalle FROM evento_documento WHERE documento_id = ? AND estado_nuevo = 'DESCARTADO'", String.class, c.id()))
                .isEqualTo("Descartado por un administrador: El cliente lo reemitió con otra serie");
    }

    @Test void unDescartadoYaNoSeReintentaNiSeDescartaDeNuevo() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        descartar(c.id(), "x");

        ResponseEntity<Map> otra = descartar(c.id(), "otra vez");
        ResponseEntity<Map> reintento = reintentar(c.id());

        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(otra)).isEqualTo("ESTADO_NO_DESCARTABLE");
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(reintento)).isEqualTo("ESTADO_NO_ENVIABLE");
        assertThat(ENVIOS.get()).isZero();
        assertThat(bitacora("DESCARTAR_COMPROBANTE")).as("solo el descarte que sí ocurrió").isEqualTo(1);
    }

    @Test void soloSeDescartaLoQueEstaEnErrorDeEnvio() {
        Cliente a = cliente("ana@negocio.pe");
        rechazadoPorFault(a, "1033");
        UUID rechazado = jdbc.queryForObject("SELECT id FROM documento WHERE estado = 'RECHAZADO'", UUID.class);

        ResponseEntity<Map> r = descartar(rechazado, "x");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("ESTADO_NO_DESCARTABLE");
        assertThat(estado(rechazado)).isEqualTo("RECHAZADO");
        assertThat(bitacora("DESCARTAR_COMPROBANTE")).isZero();
    }

    @Test void sinMotivoOConUnoMuyLargoNoSeDescartaNada() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);

        ResponseEntity<Map> sin = descartar(c.id(), null);
        ResponseEntity<Map> largo = descartar(c.id(), "x".repeat(201));
        ResponseEntity<Map> enBlanco = descartar(c.id(), "   ");

        assertThat(sin.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(sin)).isEqualTo("MOTIVO_REQUERIDO");
        assertThat(codigo(largo)).isEqualTo("MOTIVO_LARGO");
        assertThat(codigo(enBlanco)).isEqualTo("MOTIVO_REQUERIDO");
        assertThat(estado(c.id())).isEqualTo("ERROR_ENVIO");
        assertThat(enOutbox(c.id())).isEqualTo(1);
        assertThat(bitacora("DESCARTAR_COMPROBANTE")).isZero();
    }

    @Test void descartarUnComprobanteQueNoExisteEs404() {
        assertThat(descartar(UUID.randomUUID(), "x").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Si la bitácora no puede escribir, el descarte tampoco queda: ni el estado ni la salida del outbox. */
    @Test void siLaBitacoraFallaNoQuedaElDescarte() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        jdbc.update("ALTER TABLE auditoria_admin ADD CONSTRAINT ck_prueba_sin_descartes CHECK (accion <> 'DESCARTAR_COMPROBANTE')");
        try {
            ResponseEntity<Map> r = descartar(c.id(), "x");

            assertThat(r.getStatusCode().is5xxServerError()).isTrue();
            assertThat(estado(c.id())).isEqualTo("ERROR_ENVIO");
            assertThat(enOutbox(c.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_documento WHERE documento_id = ? AND estado_nuevo = 'DESCARTADO'", Long.class, c.id())).isZero();
        } finally {
            jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT ck_prueba_sin_descartes");
        }
    }

    /** Dos administradores que descartan a la vez el mismo comprobante: uno lo descarta, el otro recibe un 409 y no deja nada. Nunca un 500. */
    @Test void dosDescartesAlMismoTiempoSoloDejanUno() throws Exception {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<Map>>> futuros = new ArrayList<>();
            for (String motivo : List.of("uno", "dos")) {
                Callable<ResponseEntity<Map>> tarea = () -> { salida.await(); return descartar(c.id(), motivo); };
                futuros.add(pool.submit(tarea));
            }
            salida.countDown();
            List<HttpStatusCode> estados = new ArrayList<>();
            for (Future<ResponseEntity<Map>> f : futuros) estados.add(f.get().getStatusCode());

            assertThat(estados).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            assertThat(bitacora("DESCARTAR_COMPROBANTE")).isEqualTo(1);
            assertThat(estado(c.id())).isEqualTo("DESCARTADO");
        } finally {
            pool.shutdownNow();
        }
    }

    // --- quién lo ve ---------------------------------------------------------------------------------------------------------------------------

    /** La cola mira a todas las empresas y sus acciones tocan comprobantes ajenos: ni el dueño, ni una API key, ni una clave errónea pueden usarla. */
    @Test void soloLaPlataformaPuedeVerLaColaNiActuarSobreElla() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 1);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(a.access()), conApiKey(a.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/errores", h, null).getStatusCode()).as("ver con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/comprobantes/" + c.id() + "/reintento", h, null).getStatusCode()).as("reintentar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/comprobantes/" + c.id() + "/descarte", h, "{\"motivo\":\"x\"}").getStatusCode()).as("descartar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/comprobantes/" + c.id(), h, null).getStatusCode()).as("ver la ficha con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(ENVIOS.get()).isZero();
        assertThat(estado(c.id())).isEqualTo("ERROR_ENVIO");
    }

    // --- la ficha de un comprobante (#251) -------------------------------------------------------------------------------------------------

    /** La fila de la cola enlaza a la ficha: con su id se ve el comprobante de cualquier empresa, con su estado, intentos, último error y archivos. */
    @Test void laFichaDeUnComprobanteEnErrorDiceSuEmpresaSusIntentosYSusArchivos() {
        Cliente a = cliente("ana@negocio.pe");
        Comprobante c = enError(a, 2);

        Map<String, Object> f = datos(llamar(HttpMethod.GET, "/v1/admin/comprobantes/" + c.id(), conClaveDePlataforma(), null));

        assertThat(f).containsEntry("id", c.id().toString()).containsEntry("empresa_id", a.empresaId().toString()).containsEntry("ruc", a.ruc())
                .containsEntry("cuenta_id", a.cuentaId().toString()).containsEntry("nombre_archivo", c.nombreArchivo()).containsEntry("estado", "ERROR_ENVIO")
                .containsEntry("intentos", 2).containsEntry("tiene_xml", true).containsEntry("tiene_cdr", false).doesNotContainKey("respuesta_sunat");
        assertThat((String) f.get("ultimo_error")).contains("0109");

        // 273-H1: si el objeto se perdió del almacenamiento (lo que la verificación de integridad llama XML_FALTANTE), la ficha no dice «guardado».
        storage.borrar(c.xmlKey());
        assertThat(datos(llamar(HttpMethod.GET, "/v1/admin/comprobantes/" + c.id(), conClaveDePlataforma(), null))).containsEntry("tiene_xml", false);
    }

    @Test void laFichaDeUnComprobanteQueNoExisteEs404YUnIdMalFormadoEs400() {
        ResponseEntity<Map> noExiste = llamar(HttpMethod.GET, "/v1/admin/comprobantes/" + UUID.randomUUID(), conClaveDePlataforma(), null);
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codigo(noExiste)).isEqualTo("NO_ENCONTRADO");
        ResponseEntity<Map> malFormado = llamar(HttpMethod.GET, "/v1/admin/comprobantes/no-es-un-uuid", conClaveDePlataforma(), null);
        assertThat(malFormado.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codigo(malFormado)).isEqualTo("PARAMETRO_INVALIDO");
    }
}
