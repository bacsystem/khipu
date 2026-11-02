package pe.factura.adapters.sunatrest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pe.factura.adapters.sunatrest.CorridaDeValidez.Datos;
import pe.factura.adapters.sunatrest.CorridaDeValidez.Paso;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La prueba controlada con SUNAT real de la consulta de validez (#318). NO forma parte de {@code test}: sale a Internet y, con credenciales, lee datos reales de una
 * empresa. Se corre a propósito con {@code ./gradlew :adapters:out-sunat-rest:validezReal} (variables y qué esperar: {@code deploy/README.md}, sección 13).
 *
 * <p>Solo lee: la consulta no escribe nada en SUNAT. Sin variables corre un único paso, el de unas credenciales inventadas, que no necesita ninguna. La evidencia queda en
 * {@code build/validez-real/RESUMEN.md}, sin secretos.
 */
@Tag("sunat-real")
class ValidezRealSunatTest {
    @Test
    void laConsultaDeValidezContraSunatReal() throws IOException {
        Datos d = CorridaDeValidez.deEntorno(System.getenv());

        List<Paso> pasos = CorridaDeValidez.correr(d);

        String resumen = CorridaDeValidez.resumen(d, pasos, Instant.now());
        Path salida = Path.of(System.getProperty("validez.salida", "build/validez-real"));
        Files.createDirectories(salida);
        Files.writeString(salida.resolve("RESUMEN.md"), resumen, StandardCharsets.UTF_8);
        System.out.println(resumen);

        assertThat(pasos).filteredOn(p -> !p.cumple() && !p.informativo()).as("pasos que no cumplen (ver build/validez-real/RESUMEN.md)").isEmpty();
    }
}
