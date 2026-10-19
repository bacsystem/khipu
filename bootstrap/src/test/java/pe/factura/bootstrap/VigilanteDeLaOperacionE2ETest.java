package pe.factura.bootstrap;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.application.port.in.VigilarOperacionUseCase;
import pe.factura.application.port.in.VigilarOperacionUseCase.Resultado;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El vigilante de la operación (#341) de extremo a extremo: Postgres real con sus migraciones, el cableado real de {@code AppConfig} (umbrales, canales de correo y webhook
 * desde `app.alertas.*`) y un webhook y un correo de mentira que anotan lo que sale. Lo que importa: que un problema real de la base llegue al operador por los dos canales,
 * una sola vez, que se avise al resolverse y que el latido del respaldo que anota el script alcance para apagar la alerta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class VigilanteDeLaOperacionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    static final List<String> WEBHOOK = new CopyOnWriteArrayList<>();
    static final List<String[]> CORREOS = new CopyOnWriteArrayList<>();
    static final AtomicBoolean WEBHOOK_CAIDO = new AtomicBoolean();
    static final HttpServer SERVIDOR = levantarWebhook();

    static HttpServer levantarWebhook() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/alertas", ex -> {
                WEBHOOK.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                ex.sendResponseHeaders(WEBHOOK_CAIDO.get() ? 503 : 200, -1);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.alertas.webhook-url", () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort() + "/alertas");
        r.add("app.alertas.email", () -> "ops@khipu.pe");
        r.add("app.alertas.respaldo-horas", () -> "26");
        // El worker real no debe correr por su cuenta durante la prueba: la pasada la dispara el test.
        r.add("app.alertas.inicial-ms", () -> "86400000");
    }

    @AfterAll static void cerrar() { SERVIDOR.stop(0); }

    @TestConfiguration
    static class CorreoDePrueba {
        @Bean @Primary CorreoSender correoQueAnota() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) { CORREOS.add(new String[]{para, asunto, cuerpo}); }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new AssertionError("las alertas no llevan adjuntos"); }
            };
        }
    }

    @Autowired VigilarOperacionUseCase vigilante;
    @Autowired JdbcTemplate jdbc;

    static final AtomicLong NUMERO = new AtomicLong(1);

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, alerta_operador, latido_operativo CASCADE");
        WEBHOOK.clear();
        CORREOS.clear();
        WEBHOOK_CAIDO.set(false);
        // El respaldo exige un latido: sin él cada prueba arrancaría con esa alerta encendida.
        jdbc.update("INSERT INTO latido_operativo (clave, ultimo) VALUES ('respaldo', now())");
    }

    UUID documento(String estado) {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, ?, 'EMPRESA SAC', 'BETA')", tenant, "20" + String.format("%09d", NUMERO.getAndIncrement()));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'x')",
                id, tenant, NUMERO.getAndIncrement(), Date.valueOf(java.time.LocalDate.now()), estado);
        return id;
    }

    @Test void sinProblemasNoSaleNadaPorNingunCanal() {
        assertThat(vigilante.vigilar()).isEqualTo(new Resultado(0, 0, List.of()));
        assertThat(WEBHOOK).isEmpty();
        assertThat(CORREOS).isEmpty();
    }

    @Test void unComprobanteFueraDePlazoLlegaPorCorreoYWebhookUnaSolaVezYSeAvisaAlResolverse() {
        UUID id = documento("FUERA_DE_PLAZO");

        assertThat(vigilante.vigilar().avisadas()).isEqualTo(1);
        assertThat(WEBHOOK).singleElement().asString().contains("ALERTA").contains("FUERA_DE_PLAZO").contains("\"text\":");
        assertThat(CORREOS).singleElement().satisfies(c -> {
            assertThat(c[0]).isEqualTo("ops@khipu.pe");
            assertThat(c[1]).startsWith("[khipu] ALERTA").contains("FUERA_DE_PLAZO");
        });
        assertThat(jdbc.queryForObject("SELECT activa FROM alerta_operador WHERE clave = 'FUERA_DE_PLAZO'", Boolean.class)).isTrue();

        assertThat(vigilante.vigilar()).isEqualTo(new Resultado(0, 0, List.of()));
        assertThat(WEBHOOK).hasSize(1);
        assertThat(CORREOS).hasSize(1);

        jdbc.update("UPDATE documento SET estado = 'ACEPTADO' WHERE id = ?", id);
        assertThat(vigilante.vigilar().resueltas()).isEqualTo(1);
        assertThat(WEBHOOK).hasSize(2).last().asString().contains("RESUELTO");
        assertThat(jdbc.queryForObject("SELECT activa FROM alerta_operador WHERE clave = 'FUERA_DE_PLAZO'", Boolean.class)).isFalse();
    }

    @Test void losEnviosConErrorSoloAlertanDesdeElUmbralDeCantidadYDeTiempo() {
        for (int i = 0; i < 5; i++) documento("ERROR_ENVIO");
        assertThat(vigilante.vigilar().avisadas()).as("recién fallaron: todavía no llevan 30 min").isZero();

        jdbc.update("UPDATE documento SET updated_at = now() - interval '31 minutes' WHERE estado = 'ERROR_ENVIO' AND numero IN (SELECT numero FROM documento WHERE estado = 'ERROR_ENVIO' ORDER BY numero LIMIT 4)");
        assertThat(vigilante.vigilar().avisadas()).as("4 de 5 llevan 30 min: bajo el umbral de cantidad").isZero();

        jdbc.update("UPDATE documento SET updated_at = now() - interval '31 minutes' WHERE estado = 'ERROR_ENVIO'");
        assertThat(vigilante.vigilar().avisadas()).isEqualTo(1);
        assertThat(WEBHOOK).singleElement().asString().contains("5 comprobante(s)");
    }

    @Test void unOutboxVencidoDesdeHaceMasDeCincoMinutosAlerta() {
        UUID id = documento("FIRMADO");
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) SELECT tenant_id, id, 'ENVIAR', now() - interval '20 minutes' FROM documento WHERE id = ?", id);

        assertThat(vigilante.vigilar().avisadas()).isEqualTo(1);
        assertThat(WEBHOOK).singleElement().asString().contains("outbox").contains("1 envío(s) vencido(s)");
    }

    @Test void elRespaldoSinLatidoRecienteAlertaYElLatidoQueAnotaElScriptLoApaga() {
        jdbc.update("UPDATE latido_operativo SET ultimo = now() - interval '27 hours' WHERE clave = 'respaldo'");
        assertThat(vigilante.vigilar().avisadas()).isEqualTo(1);
        assertThat(WEBHOOK).singleElement().asString().contains("respaldo");

        // Lo mismo que hace el final de deploy/respaldo/respaldar.sh.
        jdbc.update("INSERT INTO latido_operativo (clave, ultimo) VALUES ('respaldo', now()) ON CONFLICT (clave) DO UPDATE SET ultimo = EXCLUDED.ultimo");
        assertThat(vigilante.vigilar().resueltas()).isEqualTo(1);
        assertThat(WEBHOOK).last().asString().contains("RESUELTO").contains("respaldo");
    }

    @Test void siElWebhookEstaCaidoPeroElCorreoSalioLaAlertaSeDaPorEntregadaYNoSeRepite() {
        WEBHOOK_CAIDO.set(true);
        documento("FUERA_DE_PLAZO");

        assertThat(vigilante.vigilar().avisadas()).isEqualTo(1);
        assertThat(CORREOS).hasSize(1);

        assertThat(vigilante.vigilar().avisadas()).isZero();
        assertThat(CORREOS).hasSize(1);
    }
}
