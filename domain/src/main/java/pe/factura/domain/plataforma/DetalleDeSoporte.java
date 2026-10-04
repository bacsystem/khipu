package pe.factura.domain.plataforma;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El detalle que la bitácora guarda de una sesión de soporte (#184): a qué usuario se miró y por cuánto tiempo. Un solo sitio sabe cómo se escribe y cómo se
 * lee, así quien registra la impersonación y quien se la muestra al cliente no pueden desacordarse. Nunca lleva el token. El correo no tiene espacios (el dominio
 * ya lo valida), así que el formato es inequívoco.
 */
public record DetalleDeSoporte(String usuario, long duracionSegundos) {
    private static final Pattern FORMATO = Pattern.compile("usuario=(\\S+) duracion_s=(\\d+)");

    public String texto() { return "usuario=" + usuario + " duracion_s=" + duracionSegundos; }

    /** Vacío si el texto no tiene este formato (una versión futura, o un registro que no es de esta acción): quien lo lee decide qué mostrar. */
    public static Optional<DetalleDeSoporte> leer(String texto) {
        if (texto == null) return Optional.empty();
        Matcher m = FORMATO.matcher(texto);
        if (!m.matches()) return Optional.empty();
        try {
            return Optional.of(new DetalleDeSoporte(m.group(1), Long.parseLong(m.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
