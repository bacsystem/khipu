package pe.factura.adapters.storage;

import pe.factura.application.port.out.DocumentStorage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Storage en disco local (`STORAGE_TYPE=fs`): para desarrollo y despliegues de una sola máquina. La escritura es atómica
 * (archivo temporal en el mismo directorio + rename), así un corte a mitad de escritura nunca deja un XML truncado con el
 * nombre definitivo. La conservación (backup, versionado, retención) queda a cargo del volumen; para producción está
 * {@link S3DocumentStorage} (#38).
 */
public class FileSystemDocumentStorage implements DocumentStorage {
    private final Path raiz;
    public FileSystemDocumentStorage(Path raiz) { this.raiz = raiz.toAbsolutePath().normalize(); }

    @Override public void guardar(String key, byte[] contenido) {
        Path p = resolver(key);
        try {
            Files.createDirectories(p.getParent());
            Path tmp = Files.createTempFile(p.getParent(), "." + p.getFileName(), ".tmp");
            try {
                Files.write(tmp, contenido);
                try {
                    Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) { throw new IllegalStateException("No se pudo guardar " + key, e); }
    }
    @Override public byte[] leer(String key) {
        Path p = resolver(key);
        if (!Files.exists(p)) throw new IllegalStateException("No existe " + key);
        try { return Files.readAllBytes(p); } catch (IOException e) { throw new IllegalStateException("No se pudo leer " + key, e); }
    }
    @Override public boolean existe(String key) { return Files.isRegularFile(resolver(key)); }
    @Override public void borrar(String key) {
        try { Files.deleteIfExists(resolver(key)); } catch (IOException e) { throw new IllegalStateException("No se pudo borrar " + key, e); }
    }
    private Path resolver(String key) {
        if (key == null || key.startsWith("/") || key.contains("..")) throw new IllegalArgumentException("Clave inválida: " + key);
        Path p = raiz.resolve(key).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Clave fuera de la raíz: " + key);
        return p;
    }
}
