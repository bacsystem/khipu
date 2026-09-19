package pe.factura.adapters.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class FileSystemDocumentStorageTest {
    @TempDir Path dir;

    @Test void guardaYLeeCreandoDirectorios() {
        var s = new FileSystemDocumentStorage(dir);
        s.guardar("t1/2026/09/20100066603-01-F001-1.xml", "<x/>".getBytes());
        assertThat(Files.exists(dir.resolve("t1/2026/09/20100066603-01-F001-1.xml"))).isTrue();
        assertThat(new String(s.leer("t1/2026/09/20100066603-01-F001-1.xml"))).isEqualTo("<x/>");
    }
    @Test void existeDistingueArchivosDeDirectoriosYAusentes() {
        var s = new FileSystemDocumentStorage(dir);
        s.guardar("t1/2026/09/20100066603-01-F001-1.pdf", "%PDF".getBytes());
        assertThat(s.existe("t1/2026/09/20100066603-01-F001-1.pdf")).isTrue();
        assertThat(s.existe("t1/2026/09")).isFalse();
        assertThat(s.existe("t1/2026/09/20100066603-01-F001-2.pdf")).isFalse();
    }
    @Test void borrarEliminaYEsIdempotente() {
        var s = new FileSystemDocumentStorage(dir);
        s.guardar("t1/logo-abc.png", new byte[]{1});
        s.borrar("t1/logo-abc.png");
        assertThat(s.existe("t1/logo-abc.png")).isFalse();
        s.borrar("t1/logo-abc.png");
    }
    @Test void leerInexistenteLanza() {
        assertThatThrownBy(() -> new FileSystemDocumentStorage(dir).leer("no/existe")).isInstanceOf(IllegalStateException.class);
    }
    /** Escritura atómica (#38): tras guardar no queda ningún temporal en el directorio y el contenido se reemplaza entero. */
    @Test void escribeDeFormaAtomicaSinDejarTemporales() throws Exception {
        var s = new FileSystemDocumentStorage(dir);
        s.guardar("t1/2026/09/a.xml", "<a/>".getBytes());
        s.guardar("t1/2026/09/a.xml", "<aa/>".getBytes());
        assertThat(new String(s.leer("t1/2026/09/a.xml"))).isEqualTo("<aa/>");
        try (var files = Files.list(dir.resolve("t1/2026/09"))) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("a.xml");
        }
    }
    @Test void rechazaPathTraversal() {
        var s = new FileSystemDocumentStorage(dir);
        assertThatThrownBy(() -> s.guardar("../fuera.xml", new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.guardar("/etc/passwd", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
