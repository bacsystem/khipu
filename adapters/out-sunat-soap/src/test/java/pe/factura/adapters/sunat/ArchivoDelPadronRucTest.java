package pe.factura.adapters.sunat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.factura.application.port.out.FuentePadronRuc.Descarga;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #361: el archivo del padrón viene de la descarga pública de SUNAT (.zip) o de un archivo local, y no deja temporales. */
@WireMockTest
class ArchivoDelPadronRucTest {
    static final String TEXTO = "RUC|NOMBRE|\n20100066603|MUÑOZ S.A.C.|\n";
    @TempDir Path carpeta;

    static byte[] zip(String nombre, String contenido) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(salida)) {
            z.putNextEntry(new ZipEntry(nombre));
            z.write(contenido.getBytes(StandardCharsets.ISO_8859_1));
            z.closeEntry();
        }
        return salida.toByteArray();
    }

    static String leer(Descarga d) throws IOException {
        return new String(d.contenido().readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    ArchivoDelPadronRuc fuente(String origen) { return new ArchivoDelPadronRuc(origen, Duration.ofSeconds(10)); }

    @Test void unArchivoLocalDeTextoSeLeeTalCual() throws Exception {
        Path txt = carpeta.resolve("padron.txt");
        Files.write(txt, TEXTO.getBytes(StandardCharsets.ISO_8859_1));

        try (Descarga d = fuente(txt.toString()).abrir()) {
            assertThat(leer(d)).isEqualTo(TEXTO);
            assertThat(d.origen()).isEqualTo(txt.toString());
        }
        assertThat(txt).as("un archivo local no se borra").exists();
    }

    @Test void unZipLocalSeAbreEnSuPrimerArchivo() throws Exception {
        Path z = carpeta.resolve("padron.zip");
        Files.write(z, zip("padron_reducido_ruc.txt", TEXTO));

        try (Descarga d = fuente(z.toString()).abrir()) {
            assertThat(leer(d)).isEqualTo(TEXTO);
        }
        assertThat(z).exists();
    }

    @Test void laDescargaDeSunatBajaElZipYBorraSuTemporalAlCerrar(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get("/padron_reducido_ruc.zip").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/zip").withBody(zip("padron_reducido_ruc.txt", TEXTO))));
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long antes = temporales(tmp);

        try (Descarga d = fuente(wm.getHttpBaseUrl() + "/padron_reducido_ruc.zip").abrir()) {
            assertThat(leer(d)).isEqualTo(TEXTO);
            assertThat(d.origen()).endsWith("/padron_reducido_ruc.zip");
            assertThat(temporales(tmp)).as("mientras se lee, el temporal existe").isGreaterThan(antes);
        }

        assertThat(temporales(tmp)).as("al cerrar, el temporal se borra").isEqualTo(antes);
    }

    @Test void siSunatNoRespondeBienFallaYNoDejaTemporales(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get("/padron_reducido_ruc.zip").willReturn(aResponse().withStatus(503)));
        long antes = temporales(Path.of(System.getProperty("java.io.tmpdir")));

        assertThatThrownBy(() -> fuente(wm.getHttpBaseUrl() + "/padron_reducido_ruc.zip").abrir()).isInstanceOf(IOException.class).hasMessageContaining("503");

        assertThat(temporales(Path.of(System.getProperty("java.io.tmpdir")))).isEqualTo(antes);
    }

    /** H2 de #380: el tope de la descarga cuenta también el cuerpo; las cabeceras llegan enseguida y el cuerpo gota a gota. */
    @Test void unaDescargaQueSeQuedaAMediasSeCancelaAlVencerElLimiteYNoDejaTemporales(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get("/lenta.zip").willReturn(aResponse().withStatus(200).withBody(new byte[100_000]).withChunkedDribbleDelay(10, 30_000)));
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long antes = temporales(tmp);

        long t0 = System.nanoTime();
        assertThatThrownBy(() -> new ArchivoDelPadronRuc(wm.getHttpBaseUrl() + "/lenta.zip", Duration.ofSeconds(1)).abrir())
                .isInstanceOf(IOException.class).hasMessageContaining("tardó más de 1 s");

        assertThat(Duration.ofNanos(System.nanoTime() - t0)).as("corta al vencer el límite, no espera al servidor").isLessThan(Duration.ofSeconds(10));
        assertThat(temporales(tmp)).isEqualTo(antes);
    }

    @Test void unZipVacioFalla(WireMockRuntimeInfo wm) throws Exception {
        Path z = carpeta.resolve("vacio.zip");
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        new ZipOutputStream(salida).close();
        Files.write(z, salida.toByteArray());

        assertThatThrownBy(() -> fuente(z.toString()).abrir()).isInstanceOf(IOException.class).hasMessageContaining("ningún archivo");
    }

    @Test void sinOrigenConfiguradoFalla() {
        assertThatThrownBy(() -> fuente("  ").abrir()).isInstanceOf(IOException.class).hasMessageContaining("app.padron-ruc.origen");
        assertThatThrownBy(() -> fuente(carpeta.resolve("no-existe.txt").toString()).abrir()).isInstanceOf(IOException.class);
    }

    private static long temporales(Path tmp) throws IOException {
        try (var s = Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().startsWith("padron_reducido_ruc")).count();
        }
    }
}
