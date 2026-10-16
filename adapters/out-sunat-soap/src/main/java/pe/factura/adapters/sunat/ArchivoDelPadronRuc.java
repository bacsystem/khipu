package pe.factura.adapters.sunat;

import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.out.FuentePadronRuc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * El archivo del padrón reducido (#361): la descarga pública de SUNAT ({@code https://www2.sunat.gob.pe/padron_reducido_ruc.zip}, ~400 MB, se renueva cada día) o un archivo
 * local, comprimido o no. La descarga va a un archivo temporal y no a memoria; el temporal se borra al cerrar.
 */
@Slf4j
public class ArchivoDelPadronRuc implements FuentePadronRuc {
    private final String origen;
    private final Duration limiteDeDescarga;
    private final HttpClient http;

    public ArchivoDelPadronRuc(String origen, Duration limiteDeDescarga) {
        this.origen = origen;
        this.limiteDeDescarga = limiteDeDescarga;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override public Descarga abrir() throws IOException {
        if (origen == null || origen.isBlank()) throw new IOException("No hay origen configurado para el padrón (app.padron-ruc.origen)");
        String o = origen.strip();
        if (o.regionMatches(true, 0, "http://", 0, 7) || o.regionMatches(true, 0, "https://", 0, 8)) return descargar(o);
        return abrirArchivo(Path.of(o), false, o);
    }

    private Descarga descargar(String url) throws IOException {
        Path temporal = Files.createTempFile("padron_reducido_ruc", ".tmp");
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(url)).timeout(limiteDeDescarga).GET().build();
            log.info("Descargando el padrón de RUC desde {}", url);
            HttpResponse<Path> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofFile(temporal));
            if (respuesta.statusCode() != 200) throw new IOException("SUNAT respondió HTTP " + respuesta.statusCode() + " al descargar " + url);
            return abrirArchivo(temporal, true, url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            borrar(temporal);
            throw new IOException("Se interrumpió la descarga del padrón", e);
        } catch (IOException | RuntimeException e) {
            borrar(temporal);
            throw e;
        }
    }

    /** Un {@code .zip} se abre en su primer archivo; cualquier otro se lee tal cual. */
    private static Descarga abrirArchivo(Path archivo, boolean temporal, String origen) throws IOException {
        InputStream flujo = Files.newInputStream(archivo);
        try {
            if (esZip(archivo)) {
                ZipInputStream zip = new ZipInputStream(flujo);
                flujo = zip;
                ZipEntry entrada = zip.getNextEntry();
                while (entrada != null && entrada.isDirectory()) entrada = zip.getNextEntry();
                if (entrada == null) throw new IOException("El .zip del padrón no trae ningún archivo");
            }
        } catch (IOException | RuntimeException e) {
            flujo.close();
            if (temporal) borrar(archivo);
            throw e;
        }
        InputStream contenido = flujo;
        return new Descarga() {
            @Override public InputStream contenido() { return contenido; }
            @Override public String origen() { return origen; }
            @Override public void close() throws IOException {
                try {
                    contenido.close();
                } finally {
                    if (temporal) borrar(archivo);
                }
            }
        };
    }

    private static boolean esZip(Path archivo) throws IOException {
        // Por el contenido y no por el nombre: el temporal de la descarga no tiene extensión. Un zip empieza por «PK».
        try (InputStream in = Files.newInputStream(archivo)) {
            return in.read() == 'P' && in.read() == 'K' || archivo.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
        }
    }

    private static void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar el temporal {}", archivo, e);
        }
    }
}
