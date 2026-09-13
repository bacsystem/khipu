package pe.factura.application.port.out;

public interface SecretCipher {
    byte[] cifrar(byte[] plano);
    byte[] descifrar(byte[] cifrado);
}
