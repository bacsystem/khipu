package pe.factura.application.port.out;

public record Adjunto(String nombre, String tipoContenido, byte[] contenido) {}
