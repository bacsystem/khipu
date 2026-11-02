package pe.factura.bootstrap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carga el {@code logback-spring.xml} real (sin extensiones de Spring, por eso Joran puro lo entiende) y comprueba lo que
 * importa de operar el sistema: la consola y el log de INFO cuentan el error en una línea, y la traza completa va solo al
 * log de errores (WARN y ERROR).
 */
class LogbackConfigTest {
    /** Una excepción anidada como la de un SQL roto envuelto por Spring: lo útil es la causa raíz. */
    static final Throwable ERROR_ANIDADO = new IllegalStateException("fallo envuelto",
            new IllegalArgumentException("relation \"auditoria_admin\" does not exist"));
    /** Marca de una línea de traza: las trazas de Java llevan una tabulación y «at». */
    static final String LINEA_DE_TRAZA = "\tat ";

    @TempDir Path dir;
    LoggerContext ctx;
    ByteArrayOutputStream consola;
    PrintStream salidaOriginal;
    Logger log;

    @BeforeEach void configurar() throws Exception {
        salidaOriginal = System.out;
        consola = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consola, true, StandardCharsets.UTF_8));

        URL config = getClass().getResource("/logback-spring.xml");
        assertThat(config).as("bootstrap/src/main/resources/logback-spring.xml").isNotNull();
        ctx = new LoggerContext();
        // Un contexto armado a mano no trae MDC (Spring lo inyecta en la app real): sin él cada append lanza NPE, que Logback
        // traga y deja solo en su estado.
        ctx.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        ctx.putProperty("LOG_DIR", dir.toString());
        JoranConfigurator joran = new JoranConfigurator();
        joran.setContext(ctx);
        joran.doConfigure(config);
        log = ctx.getLogger("pe.factura.prueba");
    }

    @AfterEach void cerrar() {
        var errores = erroresDeLogback();
        ctx.stop();
        System.setOut(salidaOriginal);
        // Si un appender falla al escribir, Logback no lanza: lo anota aquí. Tras cada test se exige que no haya pasado.
        assertThat(errores).as("errores internos de Logback").isEmpty();
    }

    private java.util.List<String> erroresDeLogback() {
        return ctx.getStatusManager().getCopyOfStatusList().stream()
                .filter(s -> s.getLevel() >= ch.qos.logback.core.status.Status.ERROR)
                .map(s -> s.getOrigin() + ": " + s.getMessage() + (s.getThrowable() == null ? "" : " <- " + s.getThrowable()))
                .toList();
    }

    private String consola() { return consola.toString(StandardCharsets.UTF_8); }

    private String archivo(String nombre) throws Exception {
        Path f = dir.resolve(nombre);
        return Files.exists(f) ? Files.readString(f) : "";
    }

    @Test void laConfiguracionCargaSinErroresDeLogback() {
        // Un appender mal configurado no lanza: Logback lo anota en su estado y se queda sin escribir, en silencio.
        assertThat(erroresDeLogback()).isEmpty();
    }

    @Test void unErrorNoImprimeLaTrazaPorConsolaYMuestraLaCausaRaiz() {
        log.error("Error interno trace_id=abc", ERROR_ANIDADO);

        assertThat(consola()).contains("Error interno trace_id=abc").contains("relation \"auditoria_admin\" does not exist");
        assertThat(consola()).doesNotContain(LINEA_DE_TRAZA);
    }

    @Test void elLogDeInfoTieneElErrorEnUnaLineaSinTraza() throws Exception {
        log.error("Error interno trace_id=abc", ERROR_ANIDADO);

        String info = archivo("khipu.log");
        assertThat(info).contains("Error interno trace_id=abc").contains("relation \"auditoria_admin\" does not exist");
        assertThat(info).doesNotContain(LINEA_DE_TRAZA);
    }

    @Test void laTrazaCompletaVaSoloAlLogDeErrores() throws Exception {
        log.error("Error interno trace_id=abc", ERROR_ANIDADO);

        String errores = archivo("khipu-error.log");
        assertThat(errores).contains("Error interno trace_id=abc").contains(LINEA_DE_TRAZA);
        assertThat(errores).contains("IllegalStateException: fallo envuelto").contains("relation \"auditoria_admin\" does not exist");
    }

    @Test void unWarnTambienVaAlLogDeErrores() throws Exception {
        log.warn("Reintento agotado");

        assertThat(archivo("khipu-error.log")).contains("Reintento agotado");
        assertThat(archivo("khipu.log")).contains("Reintento agotado");
    }

    @Test void unInfoVaAlLogDeInfoPeroNoAlDeErrores() throws Exception {
        log.info("Integridad del storage: 0 comprobantes verificados");

        assertThat(archivo("khipu.log")).contains("Integridad del storage");
        assertThat(archivo("khipu-error.log")).doesNotContain("Integridad del storage");
        assertThat(consola()).contains("Integridad del storage");
    }

    @Test void elDebugNoSeRegistraPorDefecto() throws Exception {
        log.debug("detalle de depuración");

        assertThat(consola()).doesNotContain("detalle de depuración");
        assertThat(archivo("khipu.log")).doesNotContain("detalle de depuración");
    }
}
