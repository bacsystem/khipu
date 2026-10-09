package pe.factura.application.port.out;

/**
 * SUNAT no acepta las credenciales SOL de la empresa (#107): un fault de autenticación de la hoja «CódigosRetorno» (0101–0106, 0110–0113, 0154) o
 * un HTTP 401 que persiste. No cambia por reintentar: hay que corregir las credenciales. Es un {@link SunatTransientException} para que quien no
 * la distingue siga tratándola como hasta ahora (el comprobante no se rechaza: queda pendiente de envío).
 */
public class SunatCredencialesException extends SunatTransientException {
    public SunatCredencialesException(String codigo, String mensaje) { super(codigo, mensaje); }
}
