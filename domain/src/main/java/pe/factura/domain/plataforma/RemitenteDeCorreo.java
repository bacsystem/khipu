package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.util.regex.Pattern;

/**
 * De quién salen los correos de la plataforma (#199): el correo remitente, el nombre con que se muestra y, si se quiere, adónde llegan las respuestas. Se valida acá porque este
 * texto termina en cabeceras del mensaje: un salto de línea o un carácter de control sería inyectar cabeceras. El correo es una sola dirección, en ASCII (sin SMTPUTF8), y el
 * nombre no lleva comillas ni ángulos.
 *
 * <p>Que el servidor SMTP acepte mandar **desde** esa dirección no se puede comprobar acá: depende de lo que el proveedor tenga autorizado (SPF/DKIM, remitente verificado).
 */
public record RemitenteDeCorreo(String nombre, String email, String responderA) {
    public static final int MAX_NOMBRE = 100;
    public static final int MAX_EMAIL = 254;

    private static final Pattern DIRECCION = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}");

    /** Valida y normaliza: sin espacios en los bordes, el nombre vacío es «sin nombre» y la respuesta vacía es «sin respuesta aparte». */
    public static RemitenteDeCorreo de(String nombre, String email, String responderA) {
        String n = nombre == null || nombre.isBlank() ? null : nombre.strip();
        if (n != null) {
            if (n.length() > MAX_NOMBRE) throw invalido("El nombre admite hasta " + MAX_NOMBRE + " caracteres");
            if (n.chars().anyMatch(Character::isISOControl) || n.matches(".*[<>\"\\\\].*")) throw invalido("El nombre no puede llevar saltos de línea, comillas ni < >");
        }
        String e = direccion(email, "El correo del remitente");
        if (e == null) throw invalido("El correo del remitente es obligatorio");
        String r = direccion(responderA, "El correo para las respuestas");
        return new RemitenteDeCorreo(n, e, r);
    }

    private static String direccion(String texto, String que) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.strip();
        if (t.length() > MAX_EMAIL || !DIRECCION.matcher(t).matches() || t.contains("..")) throw invalido(que + " no es una dirección de correo válida");
        return t;
    }

    private static DomainException invalido(String mensaje) { return new DomainException("REMITENTE_INVALIDO", mensaje); }
}
