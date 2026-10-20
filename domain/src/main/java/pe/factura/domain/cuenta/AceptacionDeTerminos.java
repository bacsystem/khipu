package pe.factura.domain.cuenta;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Que un usuario aceptó los términos del servicio y la política de privacidad (#342), en una versión y en una fecha. La versión identifica a
 * los dos textos juntos: cuando cambia cualquiera de ellos sube, y quien había aceptado la anterior tiene que volver a aceptar al entrar.
 */
public record AceptacionDeTerminos(UUID usuarioId, String version, Instant aceptadaEn) {
    /** Una fecha (p. ej. {@code 2026-10-20}) o algo parecido: corta, sin espacios, para guardarla tal cual y compararla por igualdad. */
    private static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,40}$");

    public AceptacionDeTerminos {
        if (usuarioId == null) throw new DomainException("USUARIO_REQUERIDO", "Usuario requerido");
        version = validarVersion(version);
        if (aceptadaEn == null) throw new DomainException("FECHA_REQUERIDA", "Fecha de aceptación requerida");
    }

    /** La versión vigente se configura al desplegar: una mal escrita tiene que frenar el arranque, no aparecer en el primer registro. */
    public static String validarVersion(String version) {
        if (version == null || !VERSION.matcher(version.trim()).matches())
            throw new DomainException("VERSION_TERMINOS_INVALIDA", "La versión de los términos debe tener de 1 a 40 letras, dígitos, puntos, guiones o guiones bajos");
        return version.trim();
    }
}
