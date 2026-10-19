package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.WebhookRepository.Pendiente;
import pe.factura.domain.documento.*;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EntregaWebhook.Estado;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los webhooks (#19) con Postgres real: los endpoints por empresa, el log de entregas, la toma concurrente de entregas con su marca de bloqueo y, sobre todo, que cada cambio
 * de estado de un comprobante deje su entrega en la misma transacción para los endpoints suscritos de su empresa.
 */
class JdbcWebhookRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final List<String> TODOS = List.of("comprobante.aceptado", "comprobante.rechazado");
    static final Duration BLOQUEO = Duration.ofMinutes(5);

    JdbcWebhookRepository repo = new JdbcWebhookRepository(jdbc);
    JdbcComprobanteRepository comprobantes = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    EndpointWebhook endpoint(UUID tenant, List<String> eventos) {
        EndpointWebhook e = new EndpointWebhook(UUID.randomUUID(), tenant, "https://mi-sistema.pe/khipu", "ERP", eventos, true, null, 0, AHORA);
        assertThat(uow.ejecutar(() -> repo.crearSiHayCupo(e, new byte[]{1, 2, 3}, 5))).isTrue();
        return e;
    }

    EndpointWebhook apagado(UUID tenant) {
        EndpointWebhook e = endpoint(tenant, TODOS);
        repo.fijarActivo(tenant, e.id(), false);
        return e;
    }

    private Comprobante factura(UUID t, long numero) {
        Comprobante c = Comprobante.factura(t, "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", "AV 1"),
                List.of(new Item("P1", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.GRAVADO))).crear(clock);
        c.asignarNumero(numero, "20100066603");
        c.firmar("H", "k.xml");
        return c;
    }

    int entregasDe(UUID endpoint) { return jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE endpoint_id = ?", Integer.class, endpoint); }

    List<Pendiente> tomar(int limite, int porEndpoint) { return uow.ejecutar(() -> repo.tomarVencidas(limite, porEndpoint, BLOQUEO)); }

    // --- endpoints ---------------------------------------------------------------------------------------------------------------------

    @Test void crearBuscarListarYActualizarDatos() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        endpoint(otra, TODOS);

        assertThat(repo.buscar(t, e.id())).contains(e);
        assertThat(repo.buscar(otra, e.id())).as("de otra empresa no se ve").isEmpty();
        assertThat(repo.listar(t)).containsExactly(e);
        assertThat(repo.secreto(t, e.id())).hasValueSatisfying(s -> assertThat(s).containsExactly(1, 2, 3));

        repo.actualizarDatos(t, e.id(), "https://otro.pe/h", null, List.of("comprobante.anulado"));
        EndpointWebhook leido = repo.buscar(t, e.id()).orElseThrow();
        assertThat(leido.url()).isEqualTo("https://otro.pe/h");
        assertThat(leido.descripcion()).isNull();
        assertThat(leido.eventos()).containsExactly("comprobante.anulado");
        assertThat(leido.activo()).as("los datos no tocan el estado").isTrue();

        repo.guardarSecreto(t, e.id(), new byte[]{9});
        assertThat(repo.secreto(t, e.id())).hasValueSatisfying(s -> assertThat(s).containsExactly(9));
    }

    @Test void elTopeDeEndpointsSeAplicaYSeRespetaAunqueDosAltasLlegueanALaVez() throws Exception {
        UUID t = tenantDePrueba();
        for (int i = 0; i < 3; i++) endpoint(t, TODOS);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Boolean>> altas = new ArrayList<>();
            for (int i = 0; i < 4; i++) altas.add(pool.submit(() -> {
                salida.await();
                EndpointWebhook e = new EndpointWebhook(UUID.randomUUID(), t, "https://x.pe/h", null, TODOS, true, null, 0, AHORA);
                return uow.ejecutar(() -> repo.crearSiHayCupo(e, new byte[]{1}, 5));
            }));
            salida.countDown();
            int creadas = 0;
            for (Future<Boolean> f : altas) if (f.get(30, TimeUnit.SECONDS)) creadas++;

            assertThat(creadas).as("había 3 y el tope es 5: caben dos, no cuatro").isEqualTo(2);
            assertThat(repo.listar(t)).hasSize(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void eliminarBorraElEndpointYSusEntregasSoloSiEsDeLaEmpresa() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        repo.encolar(t, e.id(), "webhook.prueba", null, "{}");

        assertThat(repo.eliminar(otra, e.id())).isFalse();
        assertThat(entregasDe(e.id())).isEqualTo(1);
        assertThat(repo.eliminar(t, e.id())).isTrue();
        assertThat(repo.listar(t)).isEmpty();
        assertThat(entregasDe(e.id())).isZero();
        assertThat(repo.eliminar(t, e.id())).isFalse();
    }

    @Test void fijarActivoApagaSinMotivoYAlEncenderPoneEnCeroYSacaLasPendientesYa() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID espera = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        UUID cerrada = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now() + interval '12 hours', locked_until = now() + interval '5 minutes' WHERE id = ?", espera);
        jdbc.update("UPDATE webhook_entrega SET estado = 'FALLIDA', siguiente_intento = now() + interval '12 hours' WHERE id = ?", cerrada);
        repo.pausar(e.id(), "Se apagó solo");
        jdbc.update("UPDATE webhook_endpoint SET fallos_consecutivos = 10 WHERE id = ?", e.id());

        repo.fijarActivo(t, e.id(), true);

        EndpointWebhook leido = repo.buscar(t, e.id()).orElseThrow();
        assertThat(leido.activo()).isTrue();
        assertThat(leido.pausadoMotivo()).isNull();
        assertThat(leido.fallosConsecutivos()).isZero();
        assertThat(jdbc.queryForObject("SELECT siguiente_intento <= now() AND locked_until IS NULL FROM webhook_entrega WHERE id = ?", Boolean.class, espera)).isTrue();
        assertThat(jdbc.queryForObject("SELECT siguiente_intento > now() FROM webhook_entrega WHERE id = ?", Boolean.class, cerrada)).as("una cerrada no se toca").isTrue();

        repo.pausar(e.id(), "otra pausa");
        repo.fijarActivo(t, e.id(), false);
        assertThat(repo.buscar(t, e.id()).orElseThrow().pausadoMotivo()).as("apagarlo a mano borra el motivo de khipu").isNull();
    }

    // --- el gancho del cambio de estado ------------------------------------------------------------------------------------------------

    @Test void unCambioDeEstadoEncolaUnaEntregaPorCadaEndpointActivoYSuscritoDeLaEmpresa() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook suscrito = endpoint(t, List.of("comprobante.aceptado"));
        EndpointWebhook otroSuscrito = endpoint(t, TODOS);
        EndpointWebhook soloRechazos = endpoint(t, List.of("comprobante.rechazado"));
        EndpointWebhook apagadoAMano = apagado(t);
        EndpointWebhook deOtra = endpoint(otra, TODOS);
        Comprobante c = factura(t, 1);
        comprobantes.guardar(c);
        c.marcarEnviado();
        comprobantes.guardar(c);
        assertThat(entregasDe(suscrito.id())).as("FIRMADO y ENVIADO no se publican").isZero();

        c.aplicarCdr(new Cdr("0", "La Factura numero F001-1, ha sido aceptada", List.of()), "r.zip");
        comprobantes.guardar(c);

        assertThat(entregasDe(suscrito.id())).isEqualTo(1);
        assertThat(entregasDe(otroSuscrito.id())).isEqualTo(1);
        assertThat(entregasDe(soloRechazos.id())).isZero();
        assertThat(entregasDe(apagadoAMano.id())).as("lo apagó una persona: no recibe nada").isZero();
        assertThat(entregasDe(deOtra.id())).as("nunca a otra empresa").isZero();
        Map<String, Object> fila = jdbc.queryForMap("SELECT evento, documento_id, estado, intentos, payload FROM webhook_entrega WHERE endpoint_id = ?", suscrito.id());
        assertThat(fila).containsEntry("evento", "comprobante.aceptado").containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0).containsEntry("documento_id", c.id());
        assertThat((String) fila.get("payload")).contains("\"evento\":\"comprobante.aceptado\"").contains("\"serie\":\"F001\"").contains("\"numero\":1").contains("\"estado\":\"ACEPTADO\"")
                .contains("\"estado_anterior\":\"ENVIADO\"").contains("\"codigo\":\"0\"").contains("\"id\":\"" + c.id() + "\"").contains("\"fecha_emision\":\"2026-09-13\"");
    }

    @Test void unEndpointPausadoPorKhipuSigueGuardandoLosEventosNuevosYSalenAlReactivarlo() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        repo.pausar(e.id(), "Se apagó solo tras 10 entregas fallidas seguidas");
        Comprobante c = factura(t, 7);
        comprobantes.guardar(c);
        c.marcarEnviado();
        comprobantes.guardar(c);

        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "r.zip");
        comprobantes.guardar(c);

        assertThat(entregasDe(e.id())).as("el evento no se pierde durante la pausa").isEqualTo(1);
        assertThat(tomar(50, 5)).as("pero mientras está pausado no sale").isEmpty();
        repo.fijarActivo(t, e.id(), true);
        assertThat(tomar(50, 5)).hasSize(1);
    }

    @Test void unReintentoQueDejaElMismoEstadoNoEsUnCambioYNoAvisa() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, List.of("comprobante.error_envio"));
        Comprobante c = factura(t, 2);
        comprobantes.guardar(c);
        c.marcarEnviado();
        comprobantes.guardar(c);

        c.marcarErrorEnvio("SUNAT no responde");
        comprobantes.guardar(c);
        c.marcarEnviado();
        comprobantes.guardar(c);
        c.marcarErrorEnvio("SUNAT no responde (2)");
        comprobantes.guardar(c);

        // ENVIADO → ERROR_ENVIO dos veces son dos cambios; ERROR_ENVIO → ERROR_ENVIO seguidos no lo serían.
        assertThat(entregasDe(e.id())).isEqualTo(2);
        c.marcarErrorEnvio("otra vez");
        comprobantes.guardar(c);
        assertThat(entregasDe(e.id())).as("ERROR_ENVIO → ERROR_ENVIO").isEqualTo(2);
    }

    @Test void siElEstadoNoSeGuardaTampocoElAviso() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        Comprobante c = factura(t, 3);
        comprobantes.guardar(c);
        c.marcarEnviado();
        comprobantes.guardar(c);
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "r.zip");

        assertThatThrownBy(() -> uow.ejecutar(() -> {
            comprobantes.guardar(c);
            throw new IllegalStateException("falla después de guardar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(entregasDe(e.id())).as("la transacción se deshizo con su aviso").isZero();
    }

    // --- el log y la toma de entregas --------------------------------------------------------------------------------------------------

    @Test void elLogPaginaFiltraPorEstadoYSoloMuestraLoDeLaEmpresa() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID a = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        UUID b = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        UUID c = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET created_at = ? WHERE id = ?", Timestamp.from(AHORA.minusSeconds(30)), a);
        jdbc.update("UPDATE webhook_entrega SET created_at = ?, estado = 'EXITOSA' WHERE id = ?", Timestamp.from(AHORA.minusSeconds(20)), b);
        jdbc.update("UPDATE webhook_entrega SET created_at = ?, estado = 'FALLIDA' WHERE id = ?", Timestamp.from(AHORA.minusSeconds(10)), c);

        assertThat(repo.entregas(t, e.id(), null, 1, 2)).extracting(EntregaWebhook::id).containsExactly(c, b);
        assertThat(repo.entregas(t, e.id(), null, 2, 2)).extracting(EntregaWebhook::id).containsExactly(a);
        assertThat(repo.contarEntregas(t, e.id(), null)).isEqualTo(3);
        assertThat(repo.entregas(t, e.id(), Estado.FALLIDA, 1, 20)).extracting(EntregaWebhook::id).containsExactly(c);
        assertThat(repo.contarEntregas(t, e.id(), Estado.PENDIENTE)).isEqualTo(1);
        assertThat(repo.entregas(otra, e.id(), null, 1, 20)).isEmpty();
        assertThat(repo.buscarEntrega(t, a)).isPresent();
        assertThat(repo.buscarEntrega(otra, a)).isEmpty();
    }

    @Test void tomarVencidasBloqueaConUnaMarcaYSoloTomaLasDeEndpointsActivos() {
        UUID t = tenantDePrueba();
        EndpointWebhook activo = endpoint(t, TODOS), apagado = apagado(t);
        UUID e1 = repo.encolar(t, activo.id(), "webhook.prueba", null, "{\"a\":1}");
        repo.encolar(t, apagado.id(), "webhook.prueba", null, "{}");
        UUID futura = repo.encolar(t, activo.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now() + interval '1 hour' WHERE id = ?", futura);

        List<Pendiente> tomadas = tomar(10, 5);

        assertThat(tomadas).singleElement().satisfies(p -> {
            assertThat(p.entrega().id()).isEqualTo(e1);
            assertThat(p.entrega().payload()).isEqualTo("{\"a\":1}");
            assertThat(p.url()).isEqualTo("https://mi-sistema.pe/khipu");
            assertThat(p.secretoCifrado()).containsExactly(1, 2, 3);
            assertThat(p.bloqueo()).isAfter(Instant.now().plus(Duration.ofMinutes(4)));
        });
        assertThat(tomar(10, 5)).as("ya bloqueada: otra instancia no la toma").isEmpty();
        assertThatThrownBy(() -> repo.tomarVencidas(10, 5, BLOQUEO)).as("exige transacción").isInstanceOf(IllegalStateException.class);
    }

    @Test void unLoteTomaComoMuchoElTopeDeCadaEndpointYNingunoAcaparaElLote() {
        UUID t = tenantDePrueba();
        EndpointWebhook lento = endpoint(t, TODOS), otro = endpoint(t, TODOS);
        for (int i = 0; i < 9; i++) repo.encolar(t, lento.id(), "webhook.prueba", null, "{}");
        for (int i = 0; i < 2; i++) repo.encolar(t, otro.id(), "webhook.prueba", null, "{}");

        List<Pendiente> lote = tomar(50, 5);

        assertThat(lote).hasSize(7);
        assertThat(lote.stream().filter(p -> p.entrega().endpointId().equals(lento.id()))).hasSize(5);
        assertThat(lote.stream().filter(p -> p.entrega().endpointId().equals(otro.id()))).hasSize(2);
        assertThat(tomar(50, 5)).as("el siguiente lote sigue con las cuatro restantes del lento").hasSize(4);
    }

    /** Con dos transacciones abiertas a la vez, cada una toma filas distintas: sin SKIP LOCKED la segunda esperaría a la primera (o tomaría las mismas). */
    @Test void dosTransaccionesAbiertasALaVezTomanEntregasDistintas() throws Exception {
        UUID t = tenantDePrueba();
        EndpointWebhook a = endpoint(t, TODOS), b = endpoint(t, TODOS);
        for (int i = 0; i < 4; i++) repo.encolar(t, a.id(), "webhook.prueba", null, "{}");
        for (int i = 0; i < 4; i++) repo.encolar(t, b.id(), "webhook.prueba", null, "{}");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch primeraTomo = new CountDownLatch(1), segundaTomo = new CountDownLatch(1);
            // La primera toma 3 de cada endpoint y sigue con su transacción abierta (filas bloqueadas) hasta que la segunda terminó de tomar.
            Future<List<Pendiente>> primera = pool.submit(() -> uow.ejecutar(() -> {
                List<Pendiente> mias = repo.tomarVencidas(6, 3, BLOQUEO);
                primeraTomo.countDown();
                try { segundaTomo.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return mias;
            }));
            assertThat(primeraTomo.await(30, TimeUnit.SECONDS)).isTrue();
            List<Pendiente> segunda;
            try {
                segunda = pool.submit(() -> uow.ejecutar(() -> repo.tomarVencidas(50, 5, BLOQUEO))).get(20, TimeUnit.SECONDS);
            } finally {
                segundaTomo.countDown();
            }
            List<Pendiente> mias = primera.get(30, TimeUnit.SECONDS);

            assertThat(mias).hasSize(6);
            assertThat(segunda).as("la segunda no esperó a la primera ni repitió sus filas").hasSize(2);
            List<UUID> todas = new ArrayList<>();
            mias.forEach(p -> todas.add(p.entrega().id()));
            segunda.forEach(p -> todas.add(p.entrega().id()));
            assertThat(todas).doesNotHaveDuplicates().hasSize(8);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void unaEntregaConElBloqueoVencidoSeVuelveATomarConOtraMarca() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente primera = tomar(10, 5).get(0);
        jdbc.update("UPDATE webhook_entrega SET locked_until = now() - interval '1 second' WHERE id = ?", id);

        Pendiente segunda = tomar(10, 5).get(0);

        assertThat(segunda.entrega().id()).isEqualTo(id);
        assertThat(segunda.bloqueo()).isNotEqualTo(primera.bloqueo());
    }

    @Test void lasEscriturasDelResultadoSoloValenConLaMarcaConQueSeTomo() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente lenta = tomar(10, 5).get(0);
        jdbc.update("UPDATE webhook_entrega SET locked_until = now() - interval '1 second' WHERE id = ?", id);
        Pendiente rapida = tomar(10, 5).get(0);

        assertThat(repo.registrarFallo(lenta, 500, "tarde", AHORA, AHORA.plusSeconds(60))).as("la instancia lenta ya no es la dueña").isEqualTo(-1);
        assertThat(repo.registrarExito(lenta, 200, AHORA)).isFalse();
        repo.liberar(lenta);
        assertThat(repo.buscarEntrega(t, id).orElseThrow().intentos()).as("nada de la lenta se anotó").isZero();
        assertThat(repo.buscar(t, e.id()).orElseThrow().fallosConsecutivos()).isZero();

        assertThat(repo.registrarExito(rapida, 200, AHORA)).isTrue();
        assertThat(repo.buscarEntrega(t, id).orElseThrow().estado()).isEqualTo(Estado.EXITOSA);
        assertThat(repo.registrarFallo(rapida, 500, "otra vez", AHORA, null)).as("una entrega ya cerrada no se reabre").isEqualTo(-1);
        assertThat(repo.buscarEntrega(t, id).orElseThrow().estado()).isEqualTo(Estado.EXITOSA);
    }

    @Test void registrarExitoCierraLaEntregaYPoneEnCeroLosFallosDelEndpoint() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente p = tomar(10, 5).get(0);
        assertThat(repo.registrarFallo(p, 500, "boom", AHORA, AHORA.plusSeconds(60))).isEqualTo(1);
        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now() WHERE id = ?", id);
        Pendiente otra = tomar(10, 5).get(0);

        assertThat(repo.registrarExito(otra, 200, AHORA.plusSeconds(61))).isTrue();

        EntregaWebhook entrega = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(entrega.estado()).isEqualTo(Estado.EXITOSA);
        assertThat(entrega.intentos()).isEqualTo(2);
        assertThat(entrega.ultimoHttp()).isEqualTo(200);
        assertThat(entrega.ultimoError()).isNull();
        assertThat(entrega.entregadaEn()).isEqualTo(AHORA.plusSeconds(61));
        assertThat(repo.buscar(t, e.id()).orElseThrow().fallosConsecutivos()).isZero();
    }

    @Test void registrarFalloReprogramaOCierraComoFallidaYCuentaLosFallosSeguidos() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente p1 = tomar(10, 5).get(0);

        int uno = repo.registrarFallo(p1, 503, "x".repeat(400), AHORA, AHORA.plusSeconds(60));
        EntregaWebhook tras1 = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(uno).isEqualTo(1);
        assertThat(tras1.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(tras1.siguienteIntento()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(tras1.ultimoError()).hasSize(300);

        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now() WHERE id = ?", id);
        Pendiente p2 = tomar(10, 5).get(0);
        int dos = repo.registrarFallo(p2, null, "sin red", AHORA.plusSeconds(60), null);
        EntregaWebhook tras2 = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(dos).isEqualTo(2);
        assertThat(tras2.estado()).isEqualTo(Estado.FALLIDA);
        assertThat(tras2.ultimoHttp()).isNull();
        assertThat(tras2.intentos()).isEqualTo(2);
        assertThat(tomar(10, 5)).as("una fallida no se vuelve a tomar").isEmpty();
    }

    @Test void liberarSueltaLaEntregaYPausarSoloAfectaAUnEndpointActivo() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente p = tomar(10, 5).get(0);
        assertThat(tomar(10, 5)).isEmpty();

        repo.liberar(p);
        assertThat(tomar(10, 5)).as("liberada: se puede volver a tomar").hasSize(1);

        assertThat(repo.estaActivo(e.id())).isTrue();
        assertThat(repo.pausar(e.id(), "Se apagó solo")).isTrue();
        assertThat(repo.pausar(e.id(), "otro motivo")).as("ya estaba apagado: no se pisa ni se avisa dos veces").isFalse();
        assertThat(repo.buscar(t, e.id()).orElseThrow().pausadoMotivo()).isEqualTo("Se apagó solo");
        assertThat(repo.estaActivo(e.id())).isFalse();
        assertThat(repo.estaActivo(UUID.randomUUID())).isFalse();
    }

    @Test void reencolarDejaLaEntregaListaParaMandarse() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        Pendiente p = tomar(10, 5).get(0);
        repo.registrarFallo(p, 500, "boom", AHORA, null);

        repo.reencolar(t, id);

        EntregaWebhook entrega = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(entrega.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(entrega.intentos()).isZero();
        assertThat(entrega.ultimoError()).isNull();
        assertThat(tomar(10, 5)).hasSize(1);
    }

    @Test void limpiarBorraLasCerradasViejasYLasPendientesViejasDeUnEndpointApagadoPeroNoLasDeUnoActivo() {
        UUID t = tenantDePrueba();
        EndpointWebhook activo = endpoint(t, TODOS), apagado = apagado(t);
        UUID viejaCerrada = repo.encolar(t, activo.id(), "webhook.prueba", null, "{}");
        UUID viejaPendienteActivo = repo.encolar(t, activo.id(), "webhook.prueba", null, "{}");
        UUID viejaPendienteApagado = repo.encolar(t, apagado.id(), "webhook.prueba", null, "{}");
        UUID reciente = repo.encolar(t, activo.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET estado = 'EXITOSA', created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(31))), viejaCerrada);
        jdbc.update("UPDATE webhook_entrega SET created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(40))), viejaPendienteActivo);
        jdbc.update("UPDATE webhook_entrega SET created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(40))), viejaPendienteApagado);
        jdbc.update("UPDATE webhook_entrega SET estado = 'FALLIDA', created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(5))), reciente);

        assertThat(repo.limpiar(AHORA.minus(Duration.ofDays(30)))).isEqualTo(2);

        assertThat(repo.buscarEntrega(t, viejaCerrada)).isEmpty();
        assertThat(repo.buscarEntrega(t, viejaPendienteApagado)).isEmpty();
        assertThat(repo.buscarEntrega(t, viejaPendienteActivo)).as("una pendiente de un endpoint activo sigue su curso").isPresent();
        assertThat(repo.buscarEntrega(t, reciente)).isPresent();
    }
}
