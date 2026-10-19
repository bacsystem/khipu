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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los webhooks (#19) con Postgres real: los endpoints por empresa, el log de entregas, la toma concurrente de entregas y, sobre todo, que cada cambio de estado de un
 * comprobante deje su entrega en la misma transacción para los endpoints activos y suscritos de su empresa.
 */
class JdbcWebhookRepositoryTest extends PersistenciaTestBase {
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final List<String> TODOS = List.of("comprobante.aceptado", "comprobante.rechazado");

    JdbcWebhookRepository repo = new JdbcWebhookRepository(jdbc);
    JdbcComprobanteRepository comprobantes = new JdbcComprobanteRepository(jdbc);
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    EndpointWebhook endpoint(UUID tenant, List<String> eventos) {
        EndpointWebhook e = new EndpointWebhook(UUID.randomUUID(), tenant, "https://mi-sistema.pe/khipu", "ERP", eventos, true, null, 0, AHORA);
        repo.crear(e, new byte[]{1, 2, 3});
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

    // --- endpoints ---------------------------------------------------------------------------------------------------------------------

    @Test void crearBuscarListarYActualizar() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        endpoint(otra, TODOS);

        assertThat(repo.buscar(t, e.id())).contains(e);
        assertThat(repo.buscar(otra, e.id())).as("de otra empresa no se ve").isEmpty();
        assertThat(repo.listar(t)).containsExactly(e);
        assertThat(repo.secreto(t, e.id())).hasValueSatisfying(s -> assertThat(s).containsExactly(1, 2, 3));

        EndpointWebhook nuevo = new EndpointWebhook(e.id(), t, "https://otro.pe/h", null, List.of("comprobante.anulado"), false, "pausado", 10, e.creadoEn());
        repo.actualizar(nuevo);
        assertThat(repo.buscar(t, e.id())).contains(nuevo);
        assertThat(repo.listar(t).get(0).eventos()).containsExactly("comprobante.anulado");

        repo.guardarSecreto(t, e.id(), new byte[]{9});
        assertThat(repo.secreto(t, e.id())).hasValueSatisfying(s -> assertThat(s).containsExactly(9));
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

    // --- el gancho del cambio de estado ------------------------------------------------------------------------------------------------

    @Test void unCambioDeEstadoEncolaUnaEntregaPorCadaEndpointActivoYSuscritoDeLaEmpresa() {
        UUID t = tenantDePrueba(), otra = tenantDePrueba();
        EndpointWebhook suscrito = endpoint(t, List.of("comprobante.aceptado"));
        EndpointWebhook otroSuscrito = endpoint(t, TODOS);
        EndpointWebhook soloRechazos = endpoint(t, List.of("comprobante.rechazado"));
        EndpointWebhook apagado = endpoint(t, TODOS);
        repo.actualizar(new EndpointWebhook(apagado.id(), t, apagado.url(), apagado.descripcion(), apagado.eventos(), false, null, 0, apagado.creadoEn()));
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
        assertThat(entregasDe(apagado.id())).isZero();
        assertThat(entregasDe(deOtra.id())).as("nunca a otra empresa").isZero();
        Map<String, Object> fila = jdbc.queryForMap("SELECT evento, documento_id, estado, intentos, payload FROM webhook_entrega WHERE endpoint_id = ?", suscrito.id());
        assertThat(fila).containsEntry("evento", "comprobante.aceptado").containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0).containsEntry("documento_id", c.id());
        assertThat((String) fila.get("payload")).contains("\"evento\":\"comprobante.aceptado\"").contains("\"serie\":\"F001\"").contains("\"numero\":1").contains("\"estado\":\"ACEPTADO\"")
                .contains("\"estado_anterior\":\"ENVIADO\"").contains("\"codigo\":\"0\"").contains("\"id\":\"" + c.id() + "\"").contains("\"fecha_emision\":\"2026-09-13\"");
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

    @Test void tomarVencidasBloqueaLasEntregasYSoloTomaLasDeEndpointsActivos() {
        UUID t = tenantDePrueba();
        EndpointWebhook activo = endpoint(t, TODOS), apagado = endpoint(t, TODOS);
        repo.actualizar(new EndpointWebhook(apagado.id(), t, apagado.url(), apagado.descripcion(), apagado.eventos(), false, null, 0, apagado.creadoEn()));
        UUID e1 = repo.encolar(t, activo.id(), "webhook.prueba", null, "{\"a\":1}");
        repo.encolar(t, apagado.id(), "webhook.prueba", null, "{}");
        UUID futura = repo.encolar(t, activo.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now() + interval '1 hour' WHERE id = ?", futura);

        List<Pendiente> tomadas = uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)));

        assertThat(tomadas).singleElement().satisfies(p -> {
            assertThat(p.entrega().id()).isEqualTo(e1);
            assertThat(p.entrega().payload()).isEqualTo("{\"a\":1}");
            assertThat(p.url()).isEqualTo("https://mi-sistema.pe/khipu");
            assertThat(p.secretoCifrado()).containsExactly(1, 2, 3);
        });
        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).as("ya bloqueada: otra instancia no la toma").isEmpty();
        assertThatThrownBy(() -> repo.tomarVencidas(10, Duration.ofMinutes(2))).as("exige transacción").isInstanceOf(IllegalStateException.class);
    }

    @Test void unaEntregaConElBloqueoVencidoSeVuelveATomar() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)));
        jdbc.update("UPDATE webhook_entrega SET locked_until = now() - interval '1 second' WHERE id = ?", id);

        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).hasSize(1);
    }

    @Test void registrarExitoCierraLaEntregaYPoneEnCeroLosFallosDelEndpoint() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        repo.registrarFallo(id, e.id(), 500, "boom", AHORA, AHORA.plusSeconds(60));
        assertThat(repo.buscar(t, e.id()).orElseThrow().fallosConsecutivos()).isEqualTo(1);

        repo.registrarExito(id, e.id(), 200, AHORA.plusSeconds(61));

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

        int uno = repo.registrarFallo(id, e.id(), 503, "x".repeat(400), AHORA, AHORA.plusSeconds(60));
        EntregaWebhook tras1 = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(uno).isEqualTo(1);
        assertThat(tras1.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(tras1.siguienteIntento()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(tras1.ultimoError()).hasSize(300);

        int dos = repo.registrarFallo(id, e.id(), null, "sin red", AHORA.plusSeconds(60), null);
        EntregaWebhook tras2 = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(dos).isEqualTo(2);
        assertThat(tras2.estado()).isEqualTo(Estado.FALLIDA);
        assertThat(tras2.ultimoHttp()).isNull();
        assertThat(tras2.intentos()).isEqualTo(2);
        assertThat(uow.ejecutar(() -> repo.tomarVencidas(10, Duration.ofMinutes(2)))).as("una fallida no se vuelve a tomar").isEmpty();
    }

    @Test void pausarApagaElEndpointConSuMotivoYReencolarDejaLaEntregaListaParaMandarse() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID id = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        repo.registrarFallo(id, e.id(), 500, "boom", AHORA, null);

        repo.pausar(e.id(), "Se apagó solo");
        repo.reencolar(t, id);

        EndpointWebhook pausado = repo.buscar(t, e.id()).orElseThrow();
        assertThat(pausado.activo()).isFalse();
        assertThat(pausado.pausadoMotivo()).isEqualTo("Se apagó solo");
        EntregaWebhook entrega = repo.buscarEntrega(t, id).orElseThrow();
        assertThat(entrega.estado()).isEqualTo(Estado.PENDIENTE);
        assertThat(entrega.intentos()).isZero();
        assertThat(entrega.ultimoError()).isNull();
    }

    @Test void limpiarBorraLasCerradasViejasYNuncaLasPendientes() {
        UUID t = tenantDePrueba();
        EndpointWebhook e = endpoint(t, TODOS);
        UUID vieja = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        UUID viejaPendiente = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        UUID reciente = repo.encolar(t, e.id(), "webhook.prueba", null, "{}");
        jdbc.update("UPDATE webhook_entrega SET estado = 'EXITOSA', created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(31))), vieja);
        jdbc.update("UPDATE webhook_entrega SET created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(40))), viejaPendiente);
        jdbc.update("UPDATE webhook_entrega SET estado = 'FALLIDA', created_at = ? WHERE id = ?", Timestamp.from(AHORA.minus(Duration.ofDays(5))), reciente);

        assertThat(repo.limpiar(AHORA.minus(Duration.ofDays(30)))).isEqualTo(1);

        assertThat(repo.buscarEntrega(t, vieja)).isEmpty();
        assertThat(repo.buscarEntrega(t, viejaPendiente)).isPresent();
        assertThat(repo.buscarEntrega(t, reciente)).isPresent();
    }
}
