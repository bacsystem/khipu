package pe.factura.application.port.out;

/**
 * Todo lo que la plataforma guarda cifrado con la MASTER_KEY (S2): certificados y su clave, credenciales SOL, secretos del segundo factor de los
 * administradores y las respuestas guardadas del alta asistida. Sirve para rotar la clave: con MASTER_KEY_ANTERIOR configurada, lo que sigue con
 * ella se vuelve a cifrar con la vigente.
 */
public interface SecretosCifradosRepository {
    /** Cuántos valores siguen cifrados con la clave anterior. Sin rotación en curso, cero. */
    int pendientes();

    /**
     * Vuelve a cifrar con la clave vigente lo que esté con la anterior y devuelve cuántos valores cambió. Idempotente: lo ya recifrado no se toca, y
     * cada fila se actualiza solo si nadie la cambió mientras tanto (dos réplicas a la vez no se pisan).
     */
    int recifrar();
}
