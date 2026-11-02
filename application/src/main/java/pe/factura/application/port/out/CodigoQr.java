package pe.factura.application.port.out;

/** Imagen PNG de un código QR. */
public interface CodigoQr {
    byte[] png(String contenido);
}
