package pe.factura.domain.tenant;

import pe.factura.domain.DomainException;

/** Logo de la representación impresa: PNG o JPEG de hasta 200 KB, reconocido por sus bytes de cabecera y no por la extensión. */
public final class LogoPdf {
    private LogoPdf() {}

    public static final int MAX_BYTES = 200 * 1024;

    /** Extensión ({@code png} o {@code jpg}) del archivo válido; {@code LOGO_INVALIDO} si no lo es. */
    public static String extension(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new DomainException("LOGO_INVALIDO", "El logo está vacío");
        if (bytes.length > MAX_BYTES) throw new DomainException("LOGO_INVALIDO", "El logo pesa " + bytes.length / 1024 + " KB; el máximo es " + MAX_BYTES / 1024 + " KB");
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "png";
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "jpg";
        throw new DomainException("LOGO_INVALIDO", "El logo debe ser PNG o JPEG");
    }

    public static String tipoContenido(String extension) { return "png".equals(extension) ? "image/png" : "image/jpeg"; }
}
