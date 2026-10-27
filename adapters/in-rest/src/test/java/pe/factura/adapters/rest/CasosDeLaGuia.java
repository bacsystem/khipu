package pe.factura.adapters.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lector de los casos de la guía para desarrolladores (`portal/.../casos-emision.ts`) que los tests de contrato pasan por el backend real. Un solo
 * sitio que conoce el formato del archivo: cada caso es un objeto con `id: "…"` y su `request` es un template literal etiquetado con {@code tecnico}
 * (#25; la etiqueta es opcional aquí porque devuelve el texto tal cual). Si el formato vuelve a cambiar, cambia aquí y no en cada test (#464 rompió
 * dos copias de este extractor a la vez).
 */
final class CasosDeLaGuia {
    static final Path ARCHIVO = Path.of("../../portal/src/components/developers/guia/casos-emision.ts");

    private CasosDeLaGuia() {
    }

    /** El template literal `request` del caso {@code id}, tal como lo muestra la guía. */
    static String request(String id) throws IOException {
        Matcher m = Pattern.compile("id: \"" + Pattern.quote(id) + "\",.*?request: (?:tecnico)?`(.*?)`,", Pattern.DOTALL).matcher(Files.readString(ARCHIVO));
        assertThat(m.find()).as("caso %s en %s", id, ARCHIVO.toAbsolutePath()).isTrue();
        assertThat(m.group(1)).as("el caso %s no interpola nada", id).doesNotContain("${");
        return m.group(1);
    }
}
