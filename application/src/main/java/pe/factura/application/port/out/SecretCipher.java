package pe.factura.application.port.out;

public interface SecretCipher {
    byte[] cifrar(byte[] plano);
    byte[] descifrar(byte[] cifrado);

    /**
     * Si {@code cifrado} está con la clave anterior y hay que volver a cifrarlo con la vigente (S2: rotación de MASTER_KEY). Lanza lo mismo que
     * {@link #descifrar} si no se puede descifrar con ninguna.
     */
    default boolean necesitaRecifrar(byte[] cifrado) { return false; }

    /** Si se está rotando: hay una clave anterior configurada además de la vigente. */
    default boolean rotando() { return false; }
}
