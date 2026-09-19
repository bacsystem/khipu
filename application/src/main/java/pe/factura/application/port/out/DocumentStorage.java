package pe.factura.application.port.out;

public interface DocumentStorage {
    void guardar(String key, byte[] contenido);
    byte[] leer(String key);
    boolean existe(String key);
    /** Elimina el archivo si existe; no falla si no está. */
    void borrar(String key);
}
