package pe.factura.adapters.storage;

import pe.factura.application.port.out.DocumentStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemDocumentStorage implements DocumentStorage {
    private final Path raiz;
    public FileSystemDocumentStorage(Path raiz) { this.raiz = raiz.toAbsolutePath().normalize(); }

    @Override public void guardar(String key, byte[] contenido) {
        Path p = resolver(key);
        try { Files.createDirectories(p.getParent()); Files.write(p, contenido); }
        catch (IOException e) { throw new IllegalStateException("No se pudo guardar " + key, e); }
    }
    @Override public byte[] leer(String key) {
        Path p = resolver(key);
        if (!Files.exists(p)) throw new IllegalStateException("No existe " + key);
        try { return Files.readAllBytes(p); } catch (IOException e) { throw new IllegalStateException("No se pudo leer " + key, e); }
    }
    private Path resolver(String key) {
        if (key == null || key.startsWith("/") || key.contains("..")) throw new IllegalArgumentException("Clave inválida: " + key);
        Path p = raiz.resolve(key).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Clave fuera de la raíz: " + key);
        return p;
    }
}
