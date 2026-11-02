package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.Locale;

/**
 * El correo del cliente que viaja con un comprobante (#325): a dónde se le envían el PDF, el XML y el CDR cuando SUNAT lo acepta, si la empresa tiene activado el envío
 * automático. Se valida con la misma regla que el correo de un cliente guardado ({@link ClienteGuardado}), para que el que el portal copia de un cliente guardado nunca se
 * rechace al emitir.
 */
public final class CorreoDelCliente {
    private CorreoDelCliente() {}

    /** En blanco o nulo, {@code null} (sin correo); si no, sin espacios en los bordes y en minúsculas. Lanza {@code RECEPTOR_INVALIDO} si no es un correo. */
    public static String normalizar(String email) {
        if (email == null || email.isBlank()) return null;
        String e = email.strip().toLowerCase(Locale.ROOT);
        if (e.length() > ClienteGuardado.EMAIL_MAXIMO || !ClienteGuardado.EMAIL.matcher(e).matches())
            throw new DomainException("RECEPTOR_INVALIDO", "El correo del cliente no es válido (hasta " + ClienteGuardado.EMAIL_MAXIMO + " caracteres): " + recortado(e));
        return e;
    }

    /** El mensaje de error repite lo recibido, pero sin dejar que un texto enorme llegue entero al log o a la respuesta. */
    private static String recortado(String e) { return e.length() <= 60 ? e : e.substring(0, 60) + "…"; }
}
